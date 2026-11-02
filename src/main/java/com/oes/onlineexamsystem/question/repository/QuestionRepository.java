package com.oes.onlineexamsystem.question.repository;

import com.oes.onlineexamsystem.question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {
    List<Question> findByCourseId(Long courseId);
    List<Question> findByBankId(Long bankId);
    long countByBankId(Long bankId);
    long countByCreatedById(Long userId);
}
