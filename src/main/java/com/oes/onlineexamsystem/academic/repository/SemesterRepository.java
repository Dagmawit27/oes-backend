package com.oes.onlineexamsystem.academic.repository;

import com.oes.onlineexamsystem.academic.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SemesterRepository extends JpaRepository<Semester, Long> {
    List<Semester> findByAcademicYearId(Long academicYearId);
    Optional<Semester> findByIsCurrentTrue();
    boolean existsByAcademicYearIdAndSemesterNumber(Long academicYearId, Integer semesterNumber);
}
