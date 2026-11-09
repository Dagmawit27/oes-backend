package com.oes.onlineexamsystem.schedule.repository;

import com.oes.onlineexamsystem.schedule.entity.EligibilityStatus;
import com.oes.onlineexamsystem.schedule.entity.ExamEligibility;
import com.oes.onlineexamsystem.schedule.entity.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamEligibilityRepository extends JpaRepository<ExamEligibility, Long> {

    List<ExamEligibility> findByScheduleIdOrderByStudentRegistrationNumberAsc(Long scheduleId);

    List<ExamEligibility> findByScheduleId(Long scheduleId);

    List<ExamEligibility> findByStudentId(Long studentId);

    long countByScheduleIdAndEligibilityStatus(Long scheduleId, EligibilityStatus status);

    Optional<ExamEligibility> findByScheduleIdAndStudentId(Long scheduleId, Long studentId);

    boolean existsByScheduleIdAndStudentId(Long scheduleId, Long studentId);

    void deleteByScheduleIdAndStudentId(Long scheduleId, Long studentId);

    @Query("""
        SELECT e FROM ExamEligibility e
        JOIN FETCH e.schedule s
        JOIN FETCH s.examination ex
        WHERE e.student.user.id = :userId
        ORDER BY s.scheduledStartAt ASC
    """)
    List<ExamEligibility> findByStudentUserIdWithSchedule(@Param("userId") Long userId);

    @Query("""
        SELECT e FROM ExamEligibility e
        JOIN e.schedule s
        WHERE e.student.id = :studentId
          AND e.eligibilityStatus = :status
          AND s.status IN :activeStatuses
          AND s.scheduledStartAt < :closeTime
          AND s.scheduledCloseAt > :startTime
          AND (:excludeScheduleId IS NULL OR s.id != :excludeScheduleId)
    """)
    List<ExamEligibility> findConflictingStudentSchedules(
            @Param("studentId") Long studentId,
            @Param("status") EligibilityStatus status,
            @Param("activeStatuses") Collection<ScheduleStatus> activeStatuses,
            @Param("startTime") Instant startTime,
            @Param("closeTime") Instant closeTime,
            @Param("excludeScheduleId") Long excludeScheduleId
    );
}
