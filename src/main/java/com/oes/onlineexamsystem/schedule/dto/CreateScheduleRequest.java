package com.oes.onlineexamsystem.schedule.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateScheduleRequest {

    @NotNull(message = "Examination ID is required")
    private Long examinationId;

    @NotNull(message = "Scheduled start time is required")
    private Instant scheduledStartAt;

    @NotNull(message = "Scheduled close time is required")
    private Instant scheduledCloseAt;

    @NotNull(message = "Duration in minutes is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer durationMinutes;

    private Long roomId;

    @Builder.Default
    private String timezone = "UTC";

    private String instructions;

    @Builder.Default
    private Boolean autoGenerateEligibility = true;
}
