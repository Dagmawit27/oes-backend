package com.oes.onlineexamsystem.execution.repository;

import com.oes.onlineexamsystem.execution.entity.StudentAnswerOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentAnswerOptionRepository extends JpaRepository<StudentAnswerOption, Long> {

    List<StudentAnswerOption> findByStudentAnswerId(Long studentAnswerId);

    void deleteByStudentAnswerId(Long studentAnswerId);
}
