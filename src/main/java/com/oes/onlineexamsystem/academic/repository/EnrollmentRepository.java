package com.oes.onlineexamsystem.academic.repository;

import com.oes.onlineexamsystem.academic.entity.Enrollment;
import com.oes.onlineexamsystem.academic.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentId(Long studentId);
    List<Enrollment> findByCourseOfferingId(Long courseOfferingId);
    Optional<Enrollment> findByStudentIdAndCourseOfferingId(Long studentId, Long courseOfferingId);
    boolean existsByStudentIdAndCourseOfferingId(Long studentId, Long courseOfferingId);
    long countByCourseOfferingIdAndStatus(Long courseOfferingId, EnrollmentStatus status);
}
