package com.oes.onlineexamsystem.academic.repository;

import com.oes.onlineexamsystem.academic.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Course> findByDepartmentId(Long departmentId);
    List<Course> findAllByIsActiveTrue();
}
