package com.oes.onlineexamsystem.execution.repository;

import com.oes.onlineexamsystem.execution.entity.StudentAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentAnswerRepository extends JpaRepository<StudentAnswer, Long> {

    Optional<StudentAnswer> findByAttemptIdAndExamQuestionId(Long attemptId, Long examQuestionId);

    @Query("SELECT sa FROM StudentAnswer sa LEFT JOIN FETCH sa.selectedOptions WHERE sa.attempt.id = :attemptId")
    List<StudentAnswer> findByAttemptIdWithSelectedOptions(@Param("attemptId") Long attemptId);

    List<StudentAnswer> findByAttemptId(Long attemptId);
}
