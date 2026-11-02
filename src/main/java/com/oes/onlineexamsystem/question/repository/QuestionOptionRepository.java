package com.oes.onlineexamsystem.question.repository;

import com.oes.onlineexamsystem.question.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
    List<QuestionOption> findByQuestionIdOrderByOptionOrderAsc(Long questionId);
    void deleteByQuestionId(Long questionId);
}
