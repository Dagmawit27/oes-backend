package com.oes.onlineexamsystem.monitoring.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamMonitoringSnapshotDto {

    private Long scheduleId;
    private Long examinationId;
    private String examinationTitle;
    private String courseCode;
    private String courseName;
    private String scheduleStatus;
    private Instant scheduledStartAt;
    private Instant scheduledCloseAt;
    private Integer durationMinutes;
    private String roomName;

    // Metrics counters
    private int totalEligibleStudents;
    private int startedCount;
    private int notStartedCount;
    private int activeAttemptsCount;
    private int submittedCount;
    private int disconnectedCount;

    // Detailed collections
    private List<CandidateMonitoringDto> candidates;
    private List<MonitoringEventDto> recentEvents;
}
