package com.oes.onlineexamsystem.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescheduleRequest {

    @NotNull(message = "New scheduled start time is required")
    private Instant scheduledStartAt;

    @NotNull(message = "New scheduled close time is required")
    private Instant scheduledCloseAt;

    private Integer durationMinutes;

    private Long roomId;

    private String instructions;

    private String reason;
}
