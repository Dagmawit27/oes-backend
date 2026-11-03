package com.oes.onlineexamsystem.monitoring.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleMonitoringSummaryDto {
    private Long scheduleId;
    private Long examinationId;
    private String examinationTitle;
    private String courseCode;
    private String courseName;
    private String scheduleStatus;
    private Instant scheduledStartAt;
    private Instant scheduledCloseAt;
    private Integer durationMinutes;
    private int totalEligible;
    private int activeAttempts;
    private int submittedCount;
}
