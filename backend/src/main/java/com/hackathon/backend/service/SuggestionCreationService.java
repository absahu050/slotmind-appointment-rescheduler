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
import com.hackathon.backend.repository.AppointmentRepository;
import com.hackathon.backend.repository.ProviderRepository;
import com.hackathon.backend.repository.SlotRepository;
import com.hackathon.backend.repository.SuggestionRepository;
import com.hackathon.backend.scheduling.SchedulingAdvisor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuggestionCreationService {
    private final AppointmentRepository appointments;
    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final SuggestionRepository suggestions;

    public SuggestionCreationService(AppointmentRepository appointments, ProviderRepository providers,
            SlotRepository slots, SuggestionRepository suggestions) {
        this.appointments = appointments;
        this.providers = providers;
        this.slots = slots;
        this.suggestions = suggestions;
    }

    @Transactional
    public Optional<RescheduleSuggestion> create(String appointmentId, TriggerReason triggerReason,
            String triggerResourceId, SchedulingAdvisor.Advice advice) {
        Appointment appointment = appointments.findByIdForUpdate(appointmentId).orElse(null);
        if (appointment == null || !triggerStillApplies(appointment, triggerReason, triggerResourceId)) {
            return Optional.empty();
        }

        boolean alreadyPending = suggestions.existsByAppointmentIdAndTriggerReasonAndStatus(
                appointmentId, triggerReason, SuggestionStatus.PENDING);
        if (alreadyPending) return Optional.empty();

        AppointmentSlot recommendedSlot = slots.findByIdForUpdate(advice.slot().getId()).orElse(null);
        if (recommendedSlot == null || recommendedSlot.getStatus() != SlotStatus.OPEN
                || recommendedSlot.getDurationMinutes() < appointment.getSlot().getDurationMinutes()) {
            return Optional.empty();
        }

        Provider recommendedProvider = providers.findByIdForUpdate(recommendedSlot.getProvider().getId()).orElse(null);
        if (recommendedProvider == null || recommendedProvider.getStatus() == ProviderStatus.OFFLINE) {
            return Optional.empty();
        }

        appointment.setStatus(AppointmentStatus.RESCHEDULE_PENDING);
        RescheduleSuggestion suggestion = new RescheduleSuggestion(UUID.randomUUID().toString(), appointment,
                recommendedSlot, advice.confidence(), advice.reasoning(), triggerReason);
        return Optional.of(suggestions.save(suggestion));
    }

    private boolean triggerStillApplies(Appointment appointment, TriggerReason reason, String resourceId) {
        return switch (reason) {
            case INITIAL -> appointment.getStatus() != AppointmentStatus.RESCHEDULED
                    && appointment.getStatus() != AppointmentStatus.COMPLETED;
            case PROVIDER_OFFLINE -> appointment.getStatus() == AppointmentStatus.CONFIRMED
                    && appointment.getProvider().getId().equals(resourceId)
                    && providers.findByIdForUpdate(resourceId)
                    .map(provider -> provider.getStatus() == ProviderStatus.OFFLINE).orElse(false);
            case SLOT_CANCELLED -> appointment.getStatus() == AppointmentStatus.CONFIRMED
                    && appointment.getSlot().getId().equals(resourceId)
                    && slots.findByIdForUpdate(resourceId)
                    .map(slot -> slot.getStatus() == SlotStatus.CANCELLED).orElse(false);
        };
    }
}
