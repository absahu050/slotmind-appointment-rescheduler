package com.hackathon.backend.repository;

import com.hackathon.backend.domain.RescheduleSuggestion;
import com.hackathon.backend.domain.SuggestionStatus;
import com.hackathon.backend.domain.TriggerReason;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {
    List<RescheduleSuggestion> findByStatusOrderByCreatedAtDesc(SuggestionStatus status);
    List<RescheduleSuggestion> findByAppointmentIdOrderByCreatedAtDesc(String appointmentId);
    @Query("select s.appointment.id from RescheduleSuggestion s where s.id = :suggestionId")
    Optional<String> findAppointmentIdBySuggestionId(@Param("suggestionId") String suggestionId);
    @Query("select s.recommendedSlot.provider.id from RescheduleSuggestion s where s.id = :suggestionId")
    Optional<String> findRecommendedProviderIdBySuggestionId(@Param("suggestionId") String suggestionId);
    @EntityGraph(attributePaths = {"recommendedSlot", "recommendedSlot.provider"})
    List<RescheduleSuggestion> findByAppointmentIdInAndStatusOrderByCreatedAtDesc(
            Collection<String> appointmentIds, SuggestionStatus status);
    boolean existsByAppointmentIdAndTriggerReasonAndStatus(String appointmentId, TriggerReason triggerReason, SuggestionStatus status);
}
