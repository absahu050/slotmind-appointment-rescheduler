package com.hackathon.backend.dto.response;

import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.RescheduleSuggestion;
import java.time.LocalDateTime;

public record SuggestionResponse(String id, String appointmentId, String slotId, String providerId,
        String providerName, LocalDateTime startTime, double confidence, String reasoning,
        String status, String triggerReason, LocalDateTime createdAt) {
    public static SuggestionResponse from(RescheduleSuggestion suggestion) {
        AppointmentSlot slot = suggestion.getRecommendedSlot();
        return new SuggestionResponse(suggestion.getId(), suggestion.getAppointment().getId(), slot.getId(),
                slot.getProvider().getId(), slot.getProvider().getName(), slot.getStartTime(),
                suggestion.getConfidence(), suggestion.getReasoning(), suggestion.getStatus().name(),
                suggestion.getTriggerReason().name(), suggestion.getCreatedAt());
    }
}
