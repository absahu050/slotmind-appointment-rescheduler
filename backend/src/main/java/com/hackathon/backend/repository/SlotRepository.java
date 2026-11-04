package com.hackathon.backend.repository;

import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.SlotStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<AppointmentSlot, String> {
    List<AppointmentSlot> findByStatusOrderByStartTimeAsc(SlotStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select s from AppointmentSlot s where s.id = :id")
    Optional<AppointmentSlot> findByIdForUpdate(@Param("id") String id);
}
