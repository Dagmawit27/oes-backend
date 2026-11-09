package com.oes.onlineexamsystem.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescheduleRequest {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate examinationDate;

    @JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime scheduledStartTime;

    private Instant scheduledStartAt;
    private Instant scheduledCloseAt;
    private Integer durationMinutes;
    private Long roomId;
    private String instructions;
    private String reason;
}
