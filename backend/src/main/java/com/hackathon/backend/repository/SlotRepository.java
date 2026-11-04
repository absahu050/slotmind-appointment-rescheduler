package com.hackathon.backend.repository;

import com.hackathon.backend.domain.AppointmentSlot;
import com.hackathon.backend.domain.Provider;
import com.hackathon.backend.domain.SlotStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;

public interface SlotRepository extends JpaRepository<AppointmentSlot, String> {
    List<AppointmentSlot> findByStatusOrderByStartTimeAsc(SlotStatus status);
    @org.springframework.data.jpa.repository.Query("select s from AppointmentSlot s where s.status = :status and s.provider = :provider order by s.startTime")
    List<AppointmentSlot> findOpenByProvider(@Param("status") SlotStatus status, @Param("provider") Provider provider);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @org.springframework.data.jpa.repository.Query("select s from AppointmentSlot s where s.id = :id")
    java.util.Optional<AppointmentSlot> findByIdForUpdate(@Param("id") String id);
}
