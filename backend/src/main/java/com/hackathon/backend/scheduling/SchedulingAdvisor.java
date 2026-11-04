package com.hackathon.backend.scheduling;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.TriggerReason;
import java.util.List;
import java.util.Optional;

public interface SchedulingAdvisor {
    Optional<Advice> recommend(Appointment appointment, TriggerReason triggerReason, List<AppointmentSlot> candidates);
    record Advice(AppointmentSlot slot, double confidence, String reasoning) { }
}
