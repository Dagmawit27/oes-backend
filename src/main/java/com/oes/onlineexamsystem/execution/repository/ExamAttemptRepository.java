package com.oes.onlineexamsystem.execution.repository;

import com.oes.onlineexamsystem.execution.entity.AttemptStatus;
import com.oes.onlineexamsystem.execution.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {

    Optional<ExamAttempt> findByStudentIdAndScheduleIdAndStatus(Long studentId, Long scheduleId, AttemptStatus status);

    List<ExamAttempt> findByStudentIdAndScheduleId(Long studentId, Long scheduleId);

    long countByStudentIdAndExaminationIdAndStatusIn(Long studentId, Long examinationId, Collection<AttemptStatus> statuses);

    List<ExamAttempt> findByStatusAndDeadlineAtBefore(AttemptStatus status, Instant now);

    List<ExamAttempt> findByStudentIdOrderByStartedAtDesc(Long studentId);

    List<ExamAttempt> findByStudentId(Long studentId);

    List<ExamAttempt> findByExaminationId(Long examinationId);

    @Query("SELECT a FROM ExamAttempt a LEFT JOIN FETCH a.answers ans LEFT JOIN FETCH ans.selectedOptions WHERE a.id = :id")
    Optional<ExamAttempt> findByIdWithAnswers(@Param("id") Long id);
}
