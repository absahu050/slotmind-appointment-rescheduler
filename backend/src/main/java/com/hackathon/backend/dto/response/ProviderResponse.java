package com.hackathon.backend.dto.response;

import com.hackathon.backend.domain.Provider;

public record ProviderResponse(String id, String name, String specialty, String status) {
    public static ProviderResponse from(Provider provider) {
        return new ProviderResponse(provider.getId(), provider.getName(), provider.getSpecialty().name(), provider.getStatus().name());
    }
}
