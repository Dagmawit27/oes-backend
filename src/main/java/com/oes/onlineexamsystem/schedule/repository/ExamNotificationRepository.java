package com.oes.onlineexamsystem.schedule.repository;

import com.oes.onlineexamsystem.schedule.entity.ExamNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamNotificationRepository extends JpaRepository<ExamNotification, Long> {

    List<ExamNotification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ExamNotification> findByScheduleId(Long scheduleId);
}
