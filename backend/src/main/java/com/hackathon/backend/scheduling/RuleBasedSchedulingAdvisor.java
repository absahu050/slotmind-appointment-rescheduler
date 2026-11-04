package com.hackathon.backend.scheduling;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.TriggerReason;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedSchedulingAdvisor implements SchedulingAdvisor {
    @Override
    public Optional<Advice> recommend(Appointment appointment, TriggerReason triggerReason, List<AppointmentSlot> candidates) {
        Comparator<AppointmentSlot> preference = Comparator
                .comparing((AppointmentSlot slot) -> !slot.getProvider().getId().equals(appointment.getProvider().getId()))
                .thenComparing(slot -> !slot.getProvider().getSpecialty().equals(appointment.getProvider().getSpecialty()))
                .thenComparing(AppointmentSlot::getStartTime);
        return candidates.stream().min(preference).map(slot -> {
            boolean sameProvider = slot.getProvider().getId().equals(appointment.getProvider().getId());
            String reason = sameProvider
                    ? "We found the next available time with your current provider, " + slot.getProvider().getName() + "."
                    : "Your provider is unavailable, so we found the earliest suitable opening with " + slot.getProvider().getName() + ".";
            return new Advice(slot, sameProvider ? 0.92 : 0.78, reason);
        });
    }
}
