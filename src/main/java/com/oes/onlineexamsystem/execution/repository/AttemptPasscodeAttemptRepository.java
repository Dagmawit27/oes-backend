package com.oes.onlineexamsystem.execution.repository;

import com.oes.onlineexamsystem.execution.entity.AttemptPasscodeAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AttemptPasscodeAttemptRepository extends JpaRepository<AttemptPasscodeAttempt, Long> {

    @Query("SELECT COUNT(a) FROM AttemptPasscodeAttempt a " +
           "WHERE a.schedule.id = :scheduleId " +
           "AND a.student.id = :studentId " +
           "AND a.isSuccessful = false " +
           "AND a.attemptedAt >= :since")
    long countRecentFailedAttempts(@Param("scheduleId") Long scheduleId,
                                  @Param("studentId") Long studentId,
                                  @Param("since") Instant since);
}
