package com.hackathon.backend.scheduling;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.TriggerReason;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "slotmind.ai", name = "enabled", havingValue = "true")
public class AiSchedulingAdvisor implements SchedulingAdvisor {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String model;
    private final String apiKey;

    public AiSchedulingAdvisor(RestClient.Builder builder, ObjectMapper mapper,
            @Value("${slotmind.ai.base-url:https://api.groq.com/openai/v1/chat/completions}") String baseUrl,
            @Value("${slotmind.ai.model:llama-3.3-70b-versatile}") String model,
            @Value("${slotmind.ai.api-key:}") String apiKey) {
        this.client = builder.baseUrl(baseUrl).build(); this.mapper = mapper; this.model = model; this.apiKey = apiKey;
    }

    @Override
    public Optional<Advice> recommend(Appointment appointment, TriggerReason triggerReason, List<AppointmentSlot> candidates) {
        if (apiKey.isBlank() || candidates.isEmpty()) return Optional.empty();
        String prompt = prompt(appointment, triggerReason, candidates);
        try {
            JsonNode response = client.post().header("Authorization", "Bearer " + apiKey)
                    .body(Map.of("model", model, "temperature", 0.2,
                            "response_format", Map.of("type", "json_object"),
                            "messages", List.of(Map.of("role", "system", "content", "Recommend only a supplied slot. Return JSON with slotId, confidence (0 to 1), and patient-friendly reasoning."),
                                    Map.of("role", "user", "content", prompt))))
                    .retrieve().body(JsonNode.class);
            String content = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText();
            JsonNode result = mapper.readTree(content.replaceAll("(?s)^```(?:json)?\\s*|\\s*```$", ""));
            String slotId = result.path("slotId").asText();
            return candidates.stream().filter(slot -> slot.getId().equals(slotId)).findFirst().map(slot -> {
                double confidence = Math.max(0, Math.min(1, result.path("confidence").asDouble(0.7)));
                String reasoning = result.path("reasoning").asText("We found an available time that fits your appointment.");
                return new Advice(slot, confidence, reasoning);
            });
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String prompt(Appointment appointment, TriggerReason reason, List<AppointmentSlot> candidates) {
        String situation = reason == TriggerReason.PROVIDER_OFFLINE
                ? "The provider is offline. Choose the earliest suitable slot, including another provider if needed."
                : reason == TriggerReason.SLOT_CANCELLED
                ? "The booked slot was cancelled. Prefer this patient's current provider when practical."
                : "Find a suitable replacement appointment time.";
        String slots = candidates.stream().map(s -> String.format("%s | %s | %s | %s | %d minutes",
                s.getId(), s.getStartTime(), s.getProvider().getName(), s.getProvider().getSpecialty(), s.getDurationMinutes())).toList().toString();
        return "Situation: " + situation + "\nPatient: " + appointment.getPatientName()
                + "\nAppointment type: " + appointment.getType() + "\nCurrent provider: " + appointment.getProvider().getName()
                + " (" + appointment.getProvider().getSpecialty() + ")\nAvailable slots:\n" + slots;
    }
}
