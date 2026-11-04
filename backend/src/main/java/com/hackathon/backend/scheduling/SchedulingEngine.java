package com.hackathon.backend.scheduling;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.ProviderStatus;
import com.hackathon.backend.domain.SlotStatus;
import com.hackathon.backend.domain.TriggerReason;
import com.hackathon.backend.repository.SlotRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SchedulingEngine {
    private final SlotRepository slots;
    private final RuleBasedSchedulingAdvisor rules;
    private final ObjectProvider<AiSchedulingAdvisor> ai;

    public SchedulingEngine(SlotRepository slots, RuleBasedSchedulingAdvisor rules, ObjectProvider<AiSchedulingAdvisor> ai) {
        this.slots = slots; this.rules = rules; this.ai = ai;
    }

    public Optional<SchedulingAdvisor.Advice> recommend(Appointment appointment, TriggerReason reason) {
        List<AppointmentSlot> candidates = slots.findByStatusOrderByStartTimeAsc(SlotStatus.OPEN).stream()
                .filter(slot -> slot.getProvider().getStatus() != ProviderStatus.OFFLINE)
                .filter(slot -> slot.getDurationMinutes() >= appointment.getSlot().getDurationMinutes())
                .toList();
        AiSchedulingAdvisor aiAdvisor = ai.getIfAvailable();
        if (aiAdvisor != null) {
            Optional<SchedulingAdvisor.Advice> advice = aiAdvisor.recommend(appointment, reason, candidates);
            if (advice.isPresent()) return advice;
        }
        return rules.recommend(appointment, reason, candidates);
    }
}
