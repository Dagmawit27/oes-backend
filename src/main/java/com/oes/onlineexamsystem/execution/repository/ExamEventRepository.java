package com.oes.onlineexamsystem.execution.repository;

import com.oes.onlineexamsystem.execution.entity.ExamEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamEventRepository extends JpaRepository<ExamEvent, Long> {

    List<ExamEvent> findByAttemptIdOrderByOccurredAtAsc(Long attemptId);
}
