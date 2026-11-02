package com.oes.onlineexamsystem.academic.repository;

import com.oes.onlineexamsystem.academic.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {
    List<CourseOffering> findBySemesterId(Long semesterId);
    List<CourseOffering> findByInstructorId(Long instructorId);
    List<CourseOffering> findByCourseId(Long courseId);
    Optional<CourseOffering> findByCourseIdAndSemesterIdAndSection(Long courseId, Long semesterId, String section);
    boolean existsByCourseIdAndSemesterIdAndSection(Long courseId, Long semesterId, String section);
}
