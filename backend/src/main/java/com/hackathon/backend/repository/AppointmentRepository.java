package com.hackathon.backend.repository;

import com.hackathon.backend.domain.Appointment;
import com.hackathon.backend.domain.AppointmentStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
    List<Appointment> findByStatusOrderBySlotStartTimeAsc(AppointmentStatus status);
    @Query("select a.id from Appointment a where a.provider.id = :providerId and a.status = :status")
    List<String> findIdsByProviderIdAndStatus(@Param("providerId") String providerId, @Param("status") AppointmentStatus status);
    @Query("select a.id from Appointment a where a.slot.id = :slotId and a.status = :status")
    List<String> findIdsBySlotIdAndStatus(@Param("slotId") String slotId, @Param("status") AppointmentStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") String id);
}
