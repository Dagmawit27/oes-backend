package com.oes.onlineexamsystem.academic.repository;

import com.oes.onlineexamsystem.academic.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByUserId(Long userId);
    Optional<Student> findByRegistrationNumberIgnoreCase(String registrationNumber);
    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);
    boolean existsByUserId(Long userId);
    List<Student> findByProgramId(Long programId);
}
