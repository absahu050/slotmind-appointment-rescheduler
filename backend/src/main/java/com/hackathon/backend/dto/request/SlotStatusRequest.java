package com.hackathon.backend.dto.request;

import com.hackathon.backend.domain.SlotStatus;
import jakarta.validation.constraints.NotNull;

public record SlotStatusRequest(@NotNull(message = "Slot status is required.") SlotStatus status) {
}
