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

@Entity
@Table(name = "appointments")
public class Appointment {
    @Id private String id;
    private String patientName;
    @Enumerated(EnumType.STRING) private AppointmentType type;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "provider_id") private Provider provider;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "slot_id") private AppointmentSlot slot;
    @Enumerated(EnumType.STRING) private AppointmentStatus status;
    @Version private long version;

    protected Appointment() { }
    public Appointment(String id, String patientName, AppointmentType type, Provider provider, AppointmentSlot slot, AppointmentStatus status) {
        this.id = id; this.patientName = patientName; this.type = type; this.provider = provider; this.slot = slot; this.status = status;
    }
    public String getId() { return id; }
    public String getPatientName() { return patientName; }
    public AppointmentType getType() { return type; }
    public Provider getProvider() { return provider; }
    public AppointmentSlot getSlot() { return slot; }
    public AppointmentStatus getStatus() { return status; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public void setSlot(AppointmentSlot slot) { this.slot = slot; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
}
