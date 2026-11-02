package com.oes.onlineexamsystem.academic.repository;

import com.oes.onlineexamsystem.academic.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramRepository extends JpaRepository<Program, Long> {
    Optional<Program> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Program> findByDepartmentId(Long departmentId);
    List<Program> findAllByIsActiveTrue();
}
