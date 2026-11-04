package com.hackathon.backend.service;

import com.hackathon.backend.api.ApiModels;
import com.hackathon.backend.api.ConflictException;
import com.hackathon.backend.api.ResourceNotFoundException;
import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.AppointmentStatus;
import com.hackathon.backend.domain.AppointmentType;
import com.hackathon.backend.domain.Provider;
import com.hackathon.backend.domain.ProviderStatus;
import com.hackathon.backend.domain.RescheduleSuggestion;
import com.hackathon.backend.domain.SlotStatus;
import com.hackathon.backend.domain.SuggestionStatus;
import com.hackathon.backend.domain.TriggerReason;
import com.hackathon.backend.repository.AppointmentRepository;
import com.hackathon.backend.repository.ProviderRepository;
import com.hackathon.backend.repository.SlotRepository;
import com.hackathon.backend.repository.SuggestionRepository;
import com.hackathon.backend.scheduling.SchedulingAdvisor;
import com.hackathon.backend.scheduling.SchedulingEngine;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SchedulingService {
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final SuggestionRepository suggestions;
    private final SchedulingEngine engine;
    private final RescheduleProcessor processor;

    public SchedulingService(ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments,
            SuggestionRepository suggestions, SchedulingEngine engine, RescheduleProcessor processor) {
        this.providers = providers; this.slots = slots; this.appointments = appointments;
        this.suggestions = suggestions; this.engine = engine; this.processor = processor;
    }

    @Transactional(readOnly = true)
    public List<ApiModels.ProviderView> providers() {
        return providers.findAll(Sort.by("name")).stream().map(ApiModels.ProviderView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiModels.SlotView> slots() {
        return slots.findAll(Sort.by("startTime")).stream().map(ApiModels.SlotView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiModels.AppointmentView> appointments(AppointmentStatus status) {
        List<Appointment> rows = status == null ? appointments.findAll(Sort.by(Sort.Direction.ASC, "slot.startTime")) : appointments.findByStatusOrderBySlotStartTimeAsc(status);
        return rows.stream().map(a -> ApiModels.AppointmentView.from(a, suggestions.findByAppointmentIdOrderByCreatedAtDesc(a.getId()).stream()
                .filter(s -> s.getStatus() == SuggestionStatus.PENDING).findFirst().orElse(null))).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiModels.SuggestionView> pendingSuggestions() {
        return suggestions.findByStatusOrderByCreatedAtDesc(SuggestionStatus.PENDING).stream().map(ApiModels.SuggestionView::from).toList();
    }

    @Transactional
    public ApiModels.AppointmentView createAppointment(ApiModels.CreateAppointmentRequest request) {
        if (request.patientName() == null || request.patientName().isBlank()) throw new IllegalArgumentException("Patient name is required.");
        Provider provider = providers.findById(request.providerId()).orElseThrow(() -> new ResourceNotFoundException("Provider not found."));
        AppointmentSlot slot = slots.findByIdForUpdate(request.slotId()).orElseThrow(() -> new ResourceNotFoundException("Slot not found."));
        if (!slot.getProvider().getId().equals(provider.getId())) throw new IllegalArgumentException("Choose a slot belonging to the selected provider.");
        ensureBookable(slot);
        slot.setStatus(SlotStatus.BOOKED);
        Appointment appointment = appointments.save(new Appointment("APT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                request.patientName().trim(), parse(AppointmentType.class, request.type(), "Appointment type"), provider, slot, AppointmentStatus.CONFIRMED));
        return ApiModels.AppointmentView.from(appointment, null);
    }

    @Transactional
    public void setProviderStatus(String id, String value) {
        Provider provider = providers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Provider not found."));
        ProviderStatus status = parse(ProviderStatus.class, value, "Provider status");
        provider.setStatus(status);
        if (status == ProviderStatus.OFFLINE) afterCommit(() -> processor.providerWentOffline(id));
    }

    @Transactional
    public void setSlotStatus(String id, String value) {
        AppointmentSlot slot = slots.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Slot not found."));
        SlotStatus status = parse(SlotStatus.class, value, "Slot status");
        if (status == SlotStatus.BOOKED) throw new IllegalArgumentException("Slots can only be booked through appointment creation.");
        slot.setStatus(status);
        if (status == SlotStatus.CANCELLED) afterCommit(() -> processor.slotWasCancelled(id));
    }

    @Transactional
    public ApiModels.SuggestionView suggest(String appointmentId) {
        Appointment appointment = appointments.findByIdForUpdate(appointmentId).orElseThrow(() -> new ResourceNotFoundException("Appointment not found."));
        RescheduleSuggestion suggestion = createSuggestion(appointment, TriggerReason.INITIAL);
        if (suggestion == null) throw new IllegalStateException("There are no suitable open slots for this appointment.");
        return ApiModels.SuggestionView.from(suggestion);
    }

    @Transactional
    public ApiModels.SuggestionView resolve(String suggestionId, String action) {
        RescheduleSuggestion suggestion = suggestions.findById(suggestionId).orElseThrow(() -> new ResourceNotFoundException("Suggestion not found."));
        if (suggestion.getStatus() != SuggestionStatus.PENDING) throw new IllegalStateException("This suggestion has already been reviewed.");
        SuggestionStatus status = parse(SuggestionStatus.class, action, "Suggestion status");
        if (status == SuggestionStatus.PENDING) throw new IllegalArgumentException("Choose ACCEPTED or REJECTED.");
        Appointment appointment = appointments.findByIdForUpdate(suggestion.getAppointment().getId()).orElseThrow(() -> new ResourceNotFoundException("Appointment not found."));
        if (status == SuggestionStatus.ACCEPTED) {
            AppointmentSlot replacement = slots.findByIdForUpdate(suggestion.getRecommendedSlot().getId()).orElseThrow(() -> new ResourceNotFoundException("Recommended slot not found."));
            ensureBookable(replacement);
            AppointmentSlot previous = slots.findByIdForUpdate(appointment.getSlot().getId()).orElseThrow(() -> new ResourceNotFoundException("Current slot not found."));
            replacement.setStatus(SlotStatus.BOOKED);
            if (previous.getStatus() == SlotStatus.BOOKED) previous.setStatus(SlotStatus.OPEN);
            appointment.setSlot(replacement);
            appointment.setProvider(replacement.getProvider());
            appointment.setStatus(AppointmentStatus.RESCHEDULED);
            suggestions.findByAppointmentIdOrderByCreatedAtDesc(appointment.getId()).stream()
                    .filter(other -> other.getStatus() == SuggestionStatus.PENDING).forEach(other -> other.setStatus(SuggestionStatus.REJECTED));
        } else {
            suggestion.setStatus(SuggestionStatus.REJECTED);
            boolean hasOtherPending = suggestions.findByAppointmentIdOrderByCreatedAtDesc(appointment.getId()).stream()
                    .anyMatch(other -> !other.getId().equals(suggestionId) && other.getStatus() == SuggestionStatus.PENDING);
            if (!hasOtherPending && appointment.getProvider().getStatus() != ProviderStatus.OFFLINE && appointment.getSlot().getStatus() == SlotStatus.BOOKED)
                appointment.setStatus(AppointmentStatus.CONFIRMED);
        }
        suggestion.setStatus(status);
        return ApiModels.SuggestionView.from(suggestion);
    }

    RescheduleSuggestion createSuggestion(Appointment appointment, TriggerReason reason) {
        if (suggestions.existsByAppointmentIdAndTriggerReasonAndStatus(appointment.getId(), reason, SuggestionStatus.PENDING)) return null;
        appointment.setStatus(AppointmentStatus.RESCHEDULE_PENDING);
        SchedulingAdvisor.Advice advice = engine.recommend(appointment, reason).orElse(null);
        if (advice == null) return null;
        RescheduleSuggestion suggestion = new RescheduleSuggestion(UUID.randomUUID().toString(), appointment, advice.slot(), advice.confidence(), advice.reasoning(), reason);
        return suggestions.save(suggestion);
    }

    private void ensureBookable(AppointmentSlot slot) {
        if (slot.getStatus() != SlotStatus.OPEN) throw new ConflictException("That slot is no longer available. Refresh and choose another.");
        if (slot.getProvider().getStatus() == ProviderStatus.OFFLINE) throw new ConflictException("This provider is currently offline.");
    }

    private void afterCommit(Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { task.run(); }
        });
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required.");
        try { return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid " + label.toLowerCase(Locale.ROOT) + "."); }
    }
}
