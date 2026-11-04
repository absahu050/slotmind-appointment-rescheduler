package com.hackathon.backend.repository;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
    List<Appointment> findByStatusOrderBySlotStartTimeAsc(AppointmentStatus status);
    List<Appointment> findByProviderIdAndStatus(String providerId, AppointmentStatus status);
    List<Appointment> findBySlotIdAndStatus(String slotId, AppointmentStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") String id);
}
