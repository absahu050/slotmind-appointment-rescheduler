package com.hackathon.backend.dto.request;

import com.hackathon.backend.domain.SuggestionStatus;
import jakarta.validation.constraints.NotNull;

public record ResolveSuggestionRequest(@NotNull(message = "Suggestion status is required.") SuggestionStatus status) {
}
