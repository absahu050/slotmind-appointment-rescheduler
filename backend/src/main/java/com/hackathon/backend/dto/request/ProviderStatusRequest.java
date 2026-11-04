package com.hackathon.backend.dto.request;

import com.hackathon.backend.domain.ProviderStatus;
import jakarta.validation.constraints.NotNull;

public record ProviderStatusRequest(@NotNull(message = "Provider status is required.") ProviderStatus status) {
}
