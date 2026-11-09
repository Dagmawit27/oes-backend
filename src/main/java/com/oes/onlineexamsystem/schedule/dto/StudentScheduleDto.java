package com.oes.onlineexamsystem.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oes.onlineexamsystem.execution.entity.AttemptStatus;
import com.oes.onlineexamsystem.schedule.entity.EligibilityStatus;
import com.oes.onlineexamsystem.schedule.entity.ScheduleStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

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

    private LocalDate examinationDate;
    private LocalTime scheduledStartTime;
    private Instant scheduledStartAt;
    private Instant scheduledCloseAt;
    private Instant startAccessEndsAt;
    private String timezone;
    private Integer durationMinutes;

    private BigDecimal totalMarks;
    private BigDecimal passingMarks;

    private String roomName;
    private String building;

    private ScheduleStatus status;
    private EligibilityStatus eligibilityStatus;
    private String eligibilityReason;
    private String instructions;

    @JsonProperty("isAvailableNow")
    private boolean isAvailableNow;

    @JsonProperty("isStartableNow")
    private boolean isStartableNow;

    @JsonProperty("isResumableNow")
    private boolean isResumableNow;

    @JsonProperty("isStartWindowClosed")
    private boolean isStartWindowClosed;

    @JsonProperty("isBeforeScheduledStart")
    private boolean isBeforeScheduledStart;

    @JsonProperty("availableNow")
    public boolean getAvailableNow() {
        return isAvailableNow;
    }

    @JsonProperty("startableNow")
    public boolean getStartableNow() {
        return isStartableNow;
    }

    @JsonProperty("resumableNow")
    public boolean getResumableNow() {
        return isResumableNow;
    }

    @JsonProperty("startWindowClosed")
    public boolean getStartWindowClosed() {
        return isStartWindowClosed;
    }

    @JsonProperty("beforeScheduledStart")
    public boolean getBeforeScheduledStart() {
        return isBeforeScheduledStart;
    }

    private AttemptStatus attemptStatus;
    private Long attemptId;
    private Integer resumeStage;
    private Integer remainingActiveSeconds;
    private Instant effectiveDeadline;
}
