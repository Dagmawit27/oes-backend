package com.oes.onlineexamsystem.schedule.repository;

import com.oes.onlineexamsystem.schedule.entity.ExamNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamNotificationRepository extends JpaRepository<ExamNotification, Long> {

    List<ExamNotification> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<ExamNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<ExamNotification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId);

    Page<ExamNotification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    List<ExamNotification> findByScheduleId(Long scheduleId);

    @Modifying
    @Query("UPDATE ExamNotification n SET n.isRead = true WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsReadForUser(@Param("userId") Long userId);
}
