package com.oes.onlineexamsystem.academic.repository;

import com.oes.onlineexamsystem.academic.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {
    Optional<AcademicYear> findByName(String name);
    Optional<AcademicYear> findByIsCurrentTrue();
    boolean existsByName(String name);
}
