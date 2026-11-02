package com.oes.onlineexamsystem.academic.repository;

import com.oes.onlineexamsystem.academic.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
    List<Department> findAllByIsActiveTrue();
}
