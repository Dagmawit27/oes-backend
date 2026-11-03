package com.oes.onlineexamsystem.execution.dto;

import com.oes.onlineexamsystem.execution.entity.ExamEventType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamEventLogRequest {

    @NotNull(message = "Event type is required")
    private ExamEventType eventType;

    private String payload;
}
