package com.oes.onlineexamsystem.result.repository;

import com.oes.onlineexamsystem.result.entity.GradeDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeDetailRepository extends JpaRepository<GradeDetail, Long> {

    List<GradeDetail> findByExamResultId(Long examResultId);

    Optional<GradeDetail> findByExamResultIdAndExamQuestionId(Long examResultId, Long examQuestionId);

    void deleteByExamResultId(Long examResultId);
}
