package com.hackathon.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

@Entity
@Table(name = "slots")
public class AppointmentSlot {
    @Id private String id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "provider_id") private Provider provider;
    private LocalDateTime startTime;
    private int durationMinutes;
    @Enumerated(EnumType.STRING) private SlotStatus status;
    @Version private long version;

    protected AppointmentSlot() { }
    public AppointmentSlot(String id, Provider provider, LocalDateTime startTime, int durationMinutes, SlotStatus status) {
        this.id = id; this.provider = provider; this.startTime = startTime; this.durationMinutes = durationMinutes; this.status = status;
    }
    public String getId() { return id; }
    public Provider getProvider() { return provider; }
    public LocalDateTime getStartTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus status) { this.status = status; }
}
