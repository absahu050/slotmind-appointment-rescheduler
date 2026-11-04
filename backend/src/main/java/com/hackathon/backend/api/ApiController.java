package com.hackathon.backend.api;

import com.hackathon.backend.domain.AppointmentStatus;
import com.hackathon.backend.service.SchedulingService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final SchedulingService service;
    public ApiController(SchedulingService service) { this.service = service; }

    @GetMapping("/providers") public List<ApiModels.ProviderView> providers() { return service.providers(); }
    @GetMapping("/slots") public List<ApiModels.SlotView> slots() { return service.slots(); }
    @GetMapping("/appointments") public List<ApiModels.AppointmentView> appointments(@RequestParam(required = false) AppointmentStatus status) { return service.appointments(status); }
    @GetMapping("/suggestions") public List<ApiModels.SuggestionView> suggestions() { return service.pendingSuggestions(); }

    @PostMapping("/appointments") @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.AppointmentView createAppointment(@RequestBody ApiModels.CreateAppointmentRequest request) { return service.createAppointment(request); }
    @PatchMapping("/providers/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProvider(@PathVariable String id, @RequestBody ApiModels.StatusRequest request) { service.setProviderStatus(id, request.status()); }
    @PatchMapping("/slots/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSlot(@PathVariable String id, @RequestBody ApiModels.StatusRequest request) { service.setSlotStatus(id, request.status()); }
    @PostMapping("/appointments/{id}/suggest") @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.SuggestionView suggest(@PathVariable String id) { return service.suggest(id); }
    @PatchMapping("/suggestions/{id}")
    public ApiModels.SuggestionView resolve(@PathVariable String id, @RequestBody ApiModels.ResolveSuggestionRequest request) { return service.resolve(id, request.status()); }
}
