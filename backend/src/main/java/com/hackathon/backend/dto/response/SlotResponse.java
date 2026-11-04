package com.hackathon.backend.dto.response;

import com.hackathon.backend.domain.AppointmentSlot;
import java.time.LocalDateTime;

public record SlotResponse(String id, String providerId, String providerName, String specialty,
        LocalDateTime startTime, int durationMinutes, String status) {
    public static SlotResponse from(AppointmentSlot slot) {
        return new SlotResponse(slot.getId(), slot.getProvider().getId(), slot.getProvider().getName(),
                slot.getProvider().getSpecialty().name(), slot.getStartTime(), slot.getDurationMinutes(), slot.getStatus().name());
    }
}
