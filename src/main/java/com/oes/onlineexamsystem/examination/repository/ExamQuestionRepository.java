package com.oes.onlineexamsystem.examination.repository;

import com.oes.onlineexamsystem.examination.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, Long> {

    List<ExamQuestion> findByExaminationIdOrderByQuestionOrderAsc(Long examinationId);

    int countByExaminationId(Long examinationId);

    void deleteByExaminationId(Long examinationId);

    boolean existsByQuestionId(Long questionId);
}
