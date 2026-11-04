package com.hackathon.backend.controller;

import com.hackathon.backend.domain.AppointmentStatus;
import com.hackathon.backend.dto.request.CreateAppointmentRequest;
import com.hackathon.backend.dto.response.AppointmentResponse;
import com.hackathon.backend.dto.response.SuggestionResponse;
import com.hackathon.backend.service.SchedulingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final SchedulingService schedulingService;

    public AppointmentController(SchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @GetMapping
    public List<AppointmentResponse> findAppointments(@RequestParam(required = false) AppointmentStatus status) {
        return schedulingService.appointments(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createAppointment(@Valid @RequestBody CreateAppointmentRequest request) {
        return schedulingService.createAppointment(request);
    }

    @PostMapping("/{id}/suggest")
    @ResponseStatus(HttpStatus.CREATED)
    public SuggestionResponse suggestReschedule(@PathVariable String id) {
        return schedulingService.suggest(id);
    }
}
