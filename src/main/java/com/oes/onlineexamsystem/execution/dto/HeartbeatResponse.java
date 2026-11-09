package com.oes.onlineexamsystem.execution.dto;

import com.oes.onlineexamsystem.execution.entity.AttemptStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeartbeatResponse {
    private Long attemptId;
    private AttemptStatus status;
    private Integer remainingActiveSeconds;
    private Integer accumulatedActiveSeconds;
    private Integer resumeStage;
    private Boolean isTerminated;
    private Instant serverCurrentTime;
}
