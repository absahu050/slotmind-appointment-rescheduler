package com.hackathon.backend.api;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.Provider;
import com.hackathon.backend.domain.RescheduleSuggestion;
import com.hackathon.backend.domain.SlotStatus;
import java.time.LocalDateTime;

public final class ApiModels {
    private ApiModels() { }
    public record ProviderView(String id, String name, String specialty, String status) {
        public static ProviderView from(Provider p) { return new ProviderView(p.getId(), p.getName(), p.getSpecialty().name(), p.getStatus().name()); }
    }
    public record SlotView(String id, String providerId, String providerName, String specialty, LocalDateTime startTime, int durationMinutes, String status) {
        public static SlotView from(AppointmentSlot s) { return new SlotView(s.getId(), s.getProvider().getId(), s.getProvider().getName(), s.getProvider().getSpecialty().name(), s.getStartTime(), s.getDurationMinutes(), s.getStatus().name()); }
    }
    public record SuggestionView(String id, String appointmentId, String slotId, String providerId, String providerName, LocalDateTime startTime, double confidence, String reasoning, String status, String triggerReason, LocalDateTime createdAt) {
        public static SuggestionView from(RescheduleSuggestion s) {
            AppointmentSlot slot = s.getRecommendedSlot();
            return new SuggestionView(s.getId(), s.getAppointment().getId(), slot.getId(), slot.getProvider().getId(), slot.getProvider().getName(), slot.getStartTime(), s.getConfidence(), s.getReasoning(), s.getStatus().name(), s.getTriggerReason().name(), s.getCreatedAt());
        }
    }
    public record AppointmentView(String id, String patientName, String type, String status, ProviderView provider, SlotView slot, SuggestionView suggestion) {
        public static AppointmentView from(Appointment a, RescheduleSuggestion s) {
            return new AppointmentView(a.getId(), a.getPatientName(), a.getType().name(), a.getStatus().name(), ProviderView.from(a.getProvider()), SlotView.from(a.getSlot()), s == null ? null : SuggestionView.from(s));
        }
    }
    public record CreateAppointmentRequest(String patientName, String type, String providerId, String slotId) { }
    public record StatusRequest(String status) { }
    public record ResolveSuggestionRequest(String status) { }
}
