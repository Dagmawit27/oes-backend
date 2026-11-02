package com.oes.onlineexamsystem.examination.repository;

import com.oes.onlineexamsystem.examination.entity.ExamApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamApprovalRepository extends JpaRepository<ExamApproval, Long> {

    List<ExamApproval> findByExaminationIdOrderByReviewedAtDesc(Long examinationId);
}
