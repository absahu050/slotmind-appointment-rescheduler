package com.hackathon.backend.service;

import com.hackathon.backend.domain.AppointmentStatus;
import com.hackathon.backend.domain.TriggerReason;
import com.hackathon.backend.repository.AppointmentRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RescheduleProcessor {
    private static final Logger log = LoggerFactory.getLogger(RescheduleProcessor.class);

    private final AppointmentRepository appointments;
    private final RescheduleSuggestionWorker worker;

    public RescheduleProcessor(AppointmentRepository appointments, RescheduleSuggestionWorker worker) {
        this.appointments = appointments;
        this.worker = worker;
    }

    @Async
    public void providerWentOffline(String providerId) {
        List<String> appointmentIds = appointments.findIdsByProviderIdAndStatus(providerId, AppointmentStatus.CONFIRMED);
        process(appointmentIds, TriggerReason.PROVIDER_OFFLINE, providerId);
        log.info("Processed {} affected appointments after provider {} went offline", appointmentIds.size(), providerId);
    }

    @Async
    public void slotWasCancelled(String slotId) {
        List<String> appointmentIds = appointments.findIdsBySlotIdAndStatus(slotId, AppointmentStatus.CONFIRMED);
        process(appointmentIds, TriggerReason.SLOT_CANCELLED, slotId);
        log.info("Processed {} affected appointments after slot {} was cancelled", appointmentIds.size(), slotId);
    }

    private void process(List<String> appointmentIds, TriggerReason reason, String triggerResourceId) {
        for (String appointmentId : appointmentIds) {
            try {
                worker.process(appointmentId, reason, triggerResourceId);
            } catch (RuntimeException exception) {
                log.error("Could not create a {} suggestion for appointment {}", reason, appointmentId, exception);
            }
        }
    }
}
