package com.hackathon.backend.repository;

import com.hackathon.backend.domain.RescheduleSuggestion;
import com.hackathon.backend.domain.SuggestionStatus;
import com.hackathon.backend.domain.TriggerReason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {
    List<RescheduleSuggestion> findByStatusOrderByCreatedAtDesc(SuggestionStatus status);
    List<RescheduleSuggestion> findByAppointmentIdOrderByCreatedAtDesc(String appointmentId);
    boolean existsByAppointmentIdAndTriggerReasonAndStatus(String appointmentId, TriggerReason triggerReason, SuggestionStatus status);
}
