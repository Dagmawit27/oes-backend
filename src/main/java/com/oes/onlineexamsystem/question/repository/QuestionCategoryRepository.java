package com.oes.onlineexamsystem.question.repository;

import com.oes.onlineexamsystem.question.entity.QuestionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionCategoryRepository extends JpaRepository<QuestionCategory, Long> {
    Optional<QuestionCategory> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<QuestionCategory> findByCourseId(Long courseId);
    List<QuestionCategory> findAllByIsActiveTrue();
}
