package com.hackathon.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "providers")
public class Provider {
    @Id private String id;
    private String name;
    @Enumerated(EnumType.STRING) private ProviderSpecialty specialty;
    @Enumerated(EnumType.STRING) private ProviderStatus status;
    @Version private long version;

    protected Provider() { }
    public Provider(String id, String name, ProviderSpecialty specialty, ProviderStatus status) {
        this.id = id; this.name = name; this.specialty = specialty; this.status = status;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public ProviderSpecialty getSpecialty() { return specialty; }
    public ProviderStatus getStatus() { return status; }
    public void setStatus(ProviderStatus status) { this.status = status; }
}
