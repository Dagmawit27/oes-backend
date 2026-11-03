package com.oes.onlineexamsystem.monitoring.dto;

import com.oes.onlineexamsystem.execution.entity.ExamEventType;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoringEventDto {
    private Long id;
    private Long attemptId;
    private Long studentId;
    private String studentName;
    private String registrationNumber;
    private ExamEventType eventType;
    private String eventPayload;
    private Instant occurredAt;
}
