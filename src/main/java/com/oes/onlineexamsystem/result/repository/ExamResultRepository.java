package com.oes.onlineexamsystem.result.repository;

import com.oes.onlineexamsystem.result.entity.ExamResult;
import com.oes.onlineexamsystem.result.entity.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamResultRepository extends JpaRepository<ExamResult, Long>, JpaSpecificationExecutor<ExamResult> {

    Optional<ExamResult> findByExaminationIdAndStudentId(Long examinationId, Long studentId);

    Optional<ExamResult> findByAttemptId(Long attemptId);

    List<ExamResult> findByExaminationId(Long examinationId);

    @Query("""
        SELECT r FROM ExamResult r
        JOIN FETCH r.examination e
        JOIN FETCH e.courseOffering co
        JOIN FETCH co.course c
        WHERE r.student.user.id = :userId
          AND r.publicationStatus = :status
        ORDER BY c.code ASC, e.title ASC
    """)
    List<ExamResult> findByStudentUserIdAndPublicationStatus(
            @Param("userId") Long userId,
            @Param("status") PublicationStatus status
    );

    @Query("""
        SELECT r FROM ExamResult r
        JOIN FETCH r.examination e
        JOIN FETCH e.courseOffering co
        JOIN FETCH co.course c
        WHERE r.student.id = :studentId
        ORDER BY c.code ASC, e.title ASC
    """)
    List<ExamResult> findByStudentId(@Param("studentId") Long studentId);

    boolean existsByExaminationIdAndStudentId(Long examinationId, Long studentId);
}
