package com.hackathon.backend.service;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentStatus;
import com.hackathon.backend.domain.ProviderStatus;
import com.hackathon.backend.domain.SlotStatus;
import com.hackathon.backend.domain.TriggerReason;
import com.hackathon.backend.repository.AppointmentRepository;
import com.hackathon.backend.scheduling.SchedulingEngine;
import org.springframework.stereotype.Service;

@Service
public class RescheduleSuggestionWorker {
    private final AppointmentRepository appointments;
    private final SchedulingEngine schedulingEngine;
    private final SuggestionCreationService suggestionCreationService;

    public RescheduleSuggestionWorker(AppointmentRepository appointments, SchedulingEngine schedulingEngine,
            SuggestionCreationService suggestionCreationService) {
        this.appointments = appointments;
        this.schedulingEngine = schedulingEngine;
        this.suggestionCreationService = suggestionCreationService;
    }

    public void process(String appointmentId, TriggerReason reason, String triggerResourceId) {
        Appointment appointment = appointments.findById(appointmentId).orElse(null);
        if (appointment == null || appointment.getStatus() != AppointmentStatus.CONFIRMED
                || !triggerMayApply(appointment, reason, triggerResourceId)) {
            return;
        }

        schedulingEngine.recommend(appointment, reason)
                .ifPresent(advice -> suggestionCreationService.create(appointmentId, reason, triggerResourceId, advice));
    }

    private boolean triggerMayApply(Appointment appointment, TriggerReason reason, String resourceId) {
        return switch (reason) {
            case PROVIDER_OFFLINE -> appointment.getProvider().getId().equals(resourceId)
                    && appointment.getProvider().getStatus() == ProviderStatus.OFFLINE;
            case SLOT_CANCELLED -> appointment.getSlot().getId().equals(resourceId)
                    && appointment.getSlot().getStatus() == SlotStatus.CANCELLED;
            case INITIAL -> false;
        };
    }
}
