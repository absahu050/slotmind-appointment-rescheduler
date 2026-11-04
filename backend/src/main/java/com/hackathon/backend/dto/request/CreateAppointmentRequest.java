package com.hackathon.backend.dto.request;

import com.hackathon.backend.domain.AppointmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAppointmentRequest(
        @NotBlank(message = "Patient name is required.")
        @Size(max = 120, message = "Patient name must be 120 characters or fewer.")
        String patientName,
        @NotNull(message = "Appointment type is required.") AppointmentType type,
        @NotBlank(message = "Provider is required.") String providerId,
        @NotBlank(message = "Appointment time is required.") String slotId) {
}
