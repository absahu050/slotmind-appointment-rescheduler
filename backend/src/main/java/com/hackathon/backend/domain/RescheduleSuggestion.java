package com.hackathon.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "reschedule_suggestions")
public class RescheduleSuggestion {
    @Id private String id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "appointment_id") private Appointment appointment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recommended_slot_id") private AppointmentSlot recommendedSlot;
    private Double confidence;
    private String reasoning;
    @Enumerated(EnumType.STRING) private SuggestionStatus status;
    @Enumerated(EnumType.STRING) private TriggerReason triggerReason;
    private LocalDateTime createdAt;

    protected RescheduleSuggestion() { }
    public RescheduleSuggestion(String id, Appointment appointment, AppointmentSlot recommendedSlot, double confidence, String reasoning, TriggerReason triggerReason) {
        this.id = id; this.appointment = appointment; this.recommendedSlot = recommendedSlot; this.confidence = confidence;
        this.reasoning = reasoning; this.triggerReason = triggerReason; this.status = SuggestionStatus.PENDING; this.createdAt = LocalDateTime.now();
    }
    public String getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public AppointmentSlot getRecommendedSlot() { return recommendedSlot; }
    public Double getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public SuggestionStatus getStatus() { return status; }
    public TriggerReason getTriggerReason() { return triggerReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setStatus(SuggestionStatus status) { this.status = status; }
}
