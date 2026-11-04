package com.hackathon.backend.service;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentStatus;
import com.hackathon.backend.domain.RescheduleSuggestion;
import com.hackathon.backend.domain.SuggestionStatus;
import com.hackathon.backend.domain.TriggerReason;
import com.hackathon.backend.repository.AppointmentRepository;
import com.hackathon.backend.repository.SuggestionRepository;
import com.hackathon.backend.scheduling.SchedulingAdvisor;
import com.hackathon.backend.scheduling.SchedulingEngine;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RescheduleProcessor {
    private static final Logger log = LoggerFactory.getLogger(RescheduleProcessor.class);
    private final AppointmentRepository appointments;
    private final SchedulingEngine engine;
    private final SuggestionRepository suggestions;

    public RescheduleProcessor(AppointmentRepository appointments, SchedulingEngine engine, SuggestionRepository suggestions) {
        this.appointments = appointments; this.engine = engine; this.suggestions = suggestions;
    }

    @Async
    @Transactional
    public void providerWentOffline(String providerId) {
        List<Appointment> affected = appointments.findByProviderIdAndStatus(providerId, AppointmentStatus.CONFIRMED);
        for (Appointment appointment : affected) create(appointment, TriggerReason.PROVIDER_OFFLINE);
        log.info("Queued {} reschedule suggestions after provider {} went offline", affected.size(), providerId);
    }

    @Async
    @Transactional
    public void slotWasCancelled(String slotId) {
        List<Appointment> affected = appointments.findBySlotIdAndStatus(slotId, AppointmentStatus.CONFIRMED);
        for (Appointment appointment : affected) create(appointment, TriggerReason.SLOT_CANCELLED);
        log.info("Queued {} reschedule suggestions after slot {} was cancelled", affected.size(), slotId);
    }

    private void create(Appointment appointment, TriggerReason reason) {
        try {
            if (suggestions.existsByAppointmentIdAndTriggerReasonAndStatus(appointment.getId(), reason, SuggestionStatus.PENDING)) return;
            appointment.setStatus(AppointmentStatus.RESCHEDULE_PENDING);
            SchedulingAdvisor.Advice advice = engine.recommend(appointment, reason).orElse(null);
            if (advice != null) suggestions.save(new RescheduleSuggestion(UUID.randomUUID().toString(), appointment,
                    advice.slot(), advice.confidence(), advice.reasoning(), reason));
        }
        catch (RuntimeException ex) { log.error("Could not create a {} suggestion for appointment {}", reason, appointment.getId(), ex); }
    }
}
