package com.hackathon.backend.service;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.AppointmentStatus;
import com.hackathon.backend.domain.Provider;
import com.hackathon.backend.domain.ProviderStatus;
import com.hackathon.backend.domain.RescheduleSuggestion;
import com.hackathon.backend.domain.SlotStatus;
import com.hackathon.backend.domain.SuggestionStatus;
import com.hackathon.backend.domain.TriggerReason;
import com.hackathon.backend.dto.request.CreateAppointmentRequest;
import com.hackathon.backend.dto.response.AppointmentResponse;
import com.hackathon.backend.dto.response.ProviderResponse;
import com.hackathon.backend.dto.response.SlotResponse;
import com.hackathon.backend.dto.response.SuggestionResponse;
import com.hackathon.backend.exception.BadRequestException;
import com.hackathon.backend.exception.ConflictException;
import com.hackathon.backend.exception.ResourceNotFoundException;
import com.hackathon.backend.repository.AppointmentRepository;
import com.hackathon.backend.repository.ProviderRepository;
import com.hackathon.backend.repository.SlotRepository;
import com.hackathon.backend.repository.SuggestionRepository;
import com.hackathon.backend.scheduling.SchedulingEngine;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SchedulingService {
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final SuggestionRepository suggestions;
    private final RescheduleProcessor processor;
    private final SchedulingEngine schedulingEngine;
    private final SuggestionCreationService suggestionCreationService;

    public SchedulingService(ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments,
            SuggestionRepository suggestions, RescheduleProcessor processor, SchedulingEngine schedulingEngine,
            SuggestionCreationService suggestionCreationService) {
        this.providers = providers;
        this.slots = slots;
        this.appointments = appointments;
        this.suggestions = suggestions;
        this.processor = processor;
        this.schedulingEngine = schedulingEngine;
        this.suggestionCreationService = suggestionCreationService;
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> providers() {
        return providers.findAll(Sort.by("name")).stream().map(ProviderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> slots() {
        return slots.findAll(Sort.by("startTime")).stream().map(SlotResponse::from).toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<AppointmentResponse> appointments(AppointmentStatus status) {
        List<Appointment> rows = status == null
                ? appointments.findAll(Sort.by(Sort.Direction.ASC, "slot.startTime"))
                : appointments.findByStatusOrderBySlotStartTimeAsc(status);
        if (rows.isEmpty()) return List.of();

        Map<String, RescheduleSuggestion> pendingByAppointment = new HashMap<>();
        suggestions.findByAppointmentIdInAndStatusOrderByCreatedAtDesc(
                rows.stream().map(Appointment::getId).toList(), SuggestionStatus.PENDING)
                .forEach(suggestion -> pendingByAppointment.putIfAbsent(
                        suggestion.getAppointment().getId(), suggestion));
        return rows.stream()
                .map(appointment -> AppointmentResponse.from(
                        appointment, pendingByAppointment.get(appointment.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> pendingSuggestions() {
        return suggestions.findByStatusOrderByCreatedAtDesc(SuggestionStatus.PENDING)
                .stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {
        if (request == null || request.patientName() == null || request.patientName().isBlank()
                || request.type() == null || request.providerId() == null || request.providerId().isBlank()
                || request.slotId() == null || request.slotId().isBlank()) {
            throw new BadRequestException("Patient name, appointment type, provider, and appointment time are required.");
        }
        Provider provider = providers.findByIdForUpdate(request.providerId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found."));
        AppointmentSlot slot = slots.findByIdForUpdate(request.slotId())
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found."));
        if (!slot.getProvider().getId().equals(provider.getId())) {
            throw new BadRequestException("Choose a slot belonging to the selected provider.");
        }
        ensureBookable(slot);
        slot.setStatus(SlotStatus.BOOKED);
        String appointmentId = "APT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Appointment appointment = appointments.save(new Appointment(appointmentId, request.patientName().trim(),
                request.type(), provider, slot, AppointmentStatus.CONFIRMED));
        return AppointmentResponse.from(appointment, null);
    }

    @Transactional
    public void setProviderStatus(String id, ProviderStatus status) {
        if (status == null) throw new BadRequestException("Provider status is required.");
        Provider provider = providers.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found."));
        boolean justWentOffline = status == ProviderStatus.OFFLINE && provider.getStatus() != ProviderStatus.OFFLINE;
        provider.setStatus(status);
        if (justWentOffline) afterCommit(() -> processor.providerWentOffline(id));
    }

    @Transactional
    public void setSlotStatus(String id, SlotStatus status) {
        if (status == null) throw new BadRequestException("Slot status is required.");
        if (status == SlotStatus.BOOKED) {
            throw new BadRequestException("Slots can only be booked through appointment creation.");
        }
        AppointmentSlot slot = slots.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found."));
        if (slot.getStatus() == SlotStatus.BOOKED && status == SlotStatus.OPEN) {
            throw new ConflictException("A booked slot cannot be reopened while an appointment still uses it.");
        }
        boolean justCancelled = status == SlotStatus.CANCELLED && slot.getStatus() != SlotStatus.CANCELLED;
        slot.setStatus(status);
        if (justCancelled) afterCommit(() -> processor.slotWasCancelled(id));
    }

    public SuggestionResponse suggest(String appointmentId) {
        Appointment appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found."));
        if (appointment.getStatus() == AppointmentStatus.RESCHEDULED
                || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new ConflictException("This appointment is no longer eligible for rescheduling.");
        }
        boolean alreadyPending = suggestions.existsByAppointmentIdAndTriggerReasonAndStatus(
                appointmentId, TriggerReason.INITIAL, SuggestionStatus.PENDING);
        if (alreadyPending) {
            throw new ConflictException("A manual reschedule suggestion is already pending.");
        }
        var advice = schedulingEngine.recommend(appointment, TriggerReason.INITIAL)
                .orElseThrow(() -> new ConflictException("There are no suitable open slots for this appointment."));
        RescheduleSuggestion suggestion = suggestionCreationService.create(
                        appointmentId, TriggerReason.INITIAL, null, advice)
                .orElseThrow(() -> new ConflictException(
                        "The appointment or recommended slot changed. Refresh and try again."));
        return SuggestionResponse.from(suggestion);
    }

    @Transactional
    public SuggestionResponse resolve(String suggestionId, SuggestionStatus status) {
        if (status == null) {
            throw new BadRequestException("Suggestion status is required.");
        }
        if (status == SuggestionStatus.PENDING) {
            throw new BadRequestException("Choose ACCEPTED or REJECTED.");
        }
        String appointmentId = suggestions.findAppointmentIdBySuggestionId(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found."));
        Appointment appointment = appointments.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found."));
        RescheduleSuggestion suggestion = suggestions.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found."));
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ConflictException("This suggestion has already been reviewed.");
        }
        if (status == SuggestionStatus.ACCEPTED) {
            String replacementProviderId = suggestions.findRecommendedProviderIdBySuggestionId(suggestionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recommended slot not found."));
            providers.findByIdForUpdate(replacementProviderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recommended provider not found."));
            AppointmentSlot replacement = slots.findByIdForUpdate(suggestion.getRecommendedSlot().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recommended slot not found."));
            ensureBookable(replacement);
            AppointmentSlot previous = slots.findByIdForUpdate(appointment.getSlot().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Current slot not found."));
            replacement.setStatus(SlotStatus.BOOKED);
            if (previous.getStatus() == SlotStatus.BOOKED) previous.setStatus(SlotStatus.OPEN);
            appointment.setSlot(replacement);
            appointment.setProvider(replacement.getProvider());
            appointment.setStatus(AppointmentStatus.RESCHEDULED);
            suggestions.findByAppointmentIdOrderByCreatedAtDesc(appointment.getId()).stream()
                    .filter(other -> other.getStatus() == SuggestionStatus.PENDING)
                    .forEach(other -> other.setStatus(SuggestionStatus.REJECTED));
        } else {
            suggestion.setStatus(SuggestionStatus.REJECTED);
            boolean hasOtherPending = suggestions.findByAppointmentIdOrderByCreatedAtDesc(appointment.getId()).stream()
                    .anyMatch(other -> !other.getId().equals(suggestionId)
                            && other.getStatus() == SuggestionStatus.PENDING);
            if (!hasOtherPending && appointment.getProvider().getStatus() != ProviderStatus.OFFLINE
                    && appointment.getSlot().getStatus() == SlotStatus.BOOKED) {
                appointment.setStatus(AppointmentStatus.CONFIRMED);
            }
        }
        suggestion.setStatus(status);
        return SuggestionResponse.from(suggestion);
    }

    private void ensureBookable(AppointmentSlot slot) {
        if (slot.getStatus() != SlotStatus.OPEN) {
            throw new ConflictException("That slot is no longer available. Refresh and choose another.");
        }
        if (slot.getProvider().getStatus() == ProviderStatus.OFFLINE) {
            throw new ConflictException("This provider is currently offline.");
        }
    }

    private void afterCommit(Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

}
