package com.hackathon.backend.dto.response;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.RescheduleSuggestion;

public record AppointmentResponse(String id, String patientName, String type, String status,
        ProviderResponse provider, SlotResponse slot, SuggestionResponse suggestion) {
    public static AppointmentResponse from(Appointment appointment, RescheduleSuggestion suggestion) {
        return new AppointmentResponse(appointment.getId(), appointment.getPatientName(), appointment.getType().name(),
                appointment.getStatus().name(), ProviderResponse.from(appointment.getProvider()),
                SlotResponse.from(appointment.getSlot()), suggestion == null ? null : SuggestionResponse.from(suggestion));
    }
}
