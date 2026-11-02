package com.oes.onlineexamsystem.schedule.dto;

import com.oes.onlineexamsystem.schedule.entity.EligibilityStatus;
import com.oes.onlineexamsystem.schedule.entity.ScheduleStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentScheduleDto {

    private Long scheduleId;
    private Long examinationId;
    private String examinationTitle;
    private String examinationDescription;

    private String courseCode;
    private String courseName;
    private String section;
    private String semesterName;

    private Instant scheduledStartAt;
    private Instant scheduledCloseAt;
    private Integer durationMinutes;

    private BigDecimal totalMarks;
    private BigDecimal passingMarks;

    private String roomName;
    private String building;

    private ScheduleStatus status;
    private EligibilityStatus eligibilityStatus;
    private String eligibilityReason;
    private String instructions;

    private boolean isAvailableNow;
    private Instant effectiveDeadline;
}
