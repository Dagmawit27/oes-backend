package com.oes.onlineexamsystem.schedule.repository;

import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import com.oes.onlineexamsystem.schedule.entity.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long>, JpaSpecificationExecutor<ExamSchedule> {

    List<ExamSchedule> findByExaminationId(Long examinationId);

    List<ExamSchedule> findByScheduledStartAtBetweenOrderByScheduledStartAtAsc(Instant start, Instant end);

    @Query("""
        SELECT s FROM ExamSchedule s
        WHERE s.room.id = :roomId
          AND s.status IN :activeStatuses
          AND s.scheduledStartAt < :closeTime
          AND s.scheduledCloseAt > :startTime
          AND (:excludeScheduleId IS NULL OR s.id != :excludeScheduleId)
    """)
    List<ExamSchedule> findConflictingRoomSchedules(
            @Param("roomId") Long roomId,
            @Param("startTime") Instant startTime,
            @Param("closeTime") Instant closeTime,
            @Param("activeStatuses") Collection<ScheduleStatus> activeStatuses,
            @Param("excludeScheduleId") Long excludeScheduleId
    );
}
