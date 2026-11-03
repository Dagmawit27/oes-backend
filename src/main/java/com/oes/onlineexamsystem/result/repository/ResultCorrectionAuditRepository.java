package com.oes.onlineexamsystem.result.repository;

import com.oes.onlineexamsystem.result.entity.ResultCorrectionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultCorrectionAuditRepository extends JpaRepository<ResultCorrectionAudit, Long> {

    List<ResultCorrectionAudit> findByExamResultIdOrderByCorrectedAtDesc(Long examResultId);
}
