package com.hackathon.backend.config;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.AppointmentStatus;
import com.hackathon.backend.domain.AppointmentType;
import com.hackathon.backend.domain.Provider;
import com.hackathon.backend.domain.ProviderSpecialty;
import com.hackathon.backend.domain.ProviderStatus;
import com.hackathon.backend.domain.SlotStatus;
import com.hackathon.backend.repository.AppointmentRepository;
import com.hackathon.backend.repository.ProviderRepository;
import com.hackathon.backend.repository.SlotRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedData {
    @Bean
    CommandLineRunner loadDemoData(ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments) {
        return args -> {
            if (providers.existsById("PRV-001")) return;
            List<Provider> providerRows = providers.saveAll(List.of(
                    new Provider("PRV-001", "Dr. Sarah Chen", ProviderSpecialty.GENERAL, ProviderStatus.BUSY),
                    new Provider("PRV-002", "Dr. James Okonkwo", ProviderSpecialty.SPECIALIST, ProviderStatus.AVAILABLE),
                    new Provider("PRV-003", "Dr. Priya Sharma", ProviderSpecialty.PEDIATRIC, ProviderStatus.BUSY),
                    new Provider("PRV-004", "Dr. Rahul Verma", ProviderSpecialty.GENERAL, ProviderStatus.AVAILABLE),
                    new Provider("PRV-005", "Dr. Ananya Iyer", ProviderSpecialty.SPECIALIST, ProviderStatus.BUSY)));
            var byId = providerRows.stream().collect(java.util.stream.Collectors.toMap(Provider::getId, p -> p));
            LocalDateTime base = LocalDateTime.now().withSecond(0).withNano(0);
            List<AppointmentSlot> slotRows = slots.saveAll(List.of(
                    new AppointmentSlot("SLT-001", byId.get("PRV-001"), base.plusHours(1), 30, SlotStatus.BOOKED),
                    new AppointmentSlot("SLT-002", byId.get("PRV-001"), base.plusHours(2), 30, SlotStatus.BOOKED),
                    new AppointmentSlot("SLT-003", byId.get("PRV-003"), base.plusHours(3), 45, SlotStatus.BOOKED),
                    new AppointmentSlot("SLT-004", byId.get("PRV-002"), base.plusHours(4), 30, SlotStatus.OPEN),
                    new AppointmentSlot("SLT-005", byId.get("PRV-004"), base.plusHours(5), 30, SlotStatus.OPEN),
                    new AppointmentSlot("SLT-006", byId.get("PRV-005"), base.plusHours(6), 60, SlotStatus.BOOKED),
                    new AppointmentSlot("SLT-007", byId.get("PRV-004"), base.plusHours(7), 45, SlotStatus.OPEN),
                    new AppointmentSlot("SLT-008", byId.get("PRV-002"), base.plusHours(8), 60, SlotStatus.OPEN)));
            var slotById = slotRows.stream().collect(java.util.stream.Collectors.toMap(AppointmentSlot::getId, s -> s));
            appointments.saveAll(List.of(
                    new Appointment("APT-001", "John Martinez", AppointmentType.ROUTINE, byId.get("PRV-001"), slotById.get("SLT-001"), AppointmentStatus.CONFIRMED),
                    new Appointment("APT-002", "Emily Watson", AppointmentType.FOLLOW_UP, byId.get("PRV-001"), slotById.get("SLT-002"), AppointmentStatus.CONFIRMED),
                    new Appointment("APT-003", "Aisha Patel", AppointmentType.URGENT, byId.get("PRV-003"), slotById.get("SLT-003"), AppointmentStatus.CONFIRMED),
                    new Appointment("APT-004", "Robert Kim", AppointmentType.ROUTINE, byId.get("PRV-005"), slotById.get("SLT-006"), AppointmentStatus.CONFIRMED)));
        };
    }
}
