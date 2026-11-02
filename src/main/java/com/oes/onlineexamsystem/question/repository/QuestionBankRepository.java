package com.oes.onlineexamsystem.question.repository;

import com.oes.onlineexamsystem.question.entity.QuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionBankRepository extends JpaRepository<QuestionBank, Long> {
    List<QuestionBank> findByCourseId(Long courseId);
    List<QuestionBank> findByCreatedById(Long userId);
}
