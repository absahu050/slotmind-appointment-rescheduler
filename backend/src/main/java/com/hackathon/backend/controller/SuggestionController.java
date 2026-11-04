package com.hackathon.backend.controller;

import com.hackathon.backend.dto.request.ResolveSuggestionRequest;
import com.hackathon.backend.dto.response.SuggestionResponse;
import com.hackathon.backend.service.SchedulingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suggestions")
public class SuggestionController {
    private final SchedulingService schedulingService;

    public SuggestionController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @GetMapping
    public List<SuggestionResponse> findPendingSuggestions() {
        return schedulingService.pendingSuggestions();
    }

    @PatchMapping("/{id}")
    public SuggestionResponse resolve(@PathVariable String id, @Valid @RequestBody ResolveSuggestionRequest request) {
        return schedulingService.resolve(id, request.status());
    }
}
