package com.oes.onlineexamsystem.schedule.repository;

import com.oes.onlineexamsystem.schedule.entity.ExamRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamRoomRepository extends JpaRepository<ExamRoom, Long> {

    Optional<ExamRoom> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<ExamRoom> findByIsActiveTrueOrderByNameAsc();
}
