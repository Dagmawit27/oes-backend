package com.oes.onlineexamsystem.academic.repository;

import com.oes.onlineexamsystem.academic.entity.Instructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstructorRepository extends JpaRepository<Instructor, Long> {
    Optional<Instructor> findByUserId(Long userId);
    Optional<Instructor> findByEmployeeIdIgnoreCase(String employeeId);
    boolean existsByEmployeeIdIgnoreCase(String employeeId);
    boolean existsByUserId(Long userId);
    List<Instructor> findByDepartmentId(Long departmentId);
}
