package com.hackathon.backend.controller;

import com.hackathon.backend.dto.request.ProviderStatusRequest;
import com.hackathon.backend.dto.response.ProviderResponse;
import com.hackathon.backend.service.SchedulingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {
    private final SchedulingService schedulingService;

    public ProviderController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @GetMapping
    public List<ProviderResponse> findProviders() {
        return schedulingService.providers();
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateStatus(@PathVariable String id, @Valid @RequestBody ProviderStatusRequest request) {
        schedulingService.setProviderStatus(id, request.status());
    }
}
