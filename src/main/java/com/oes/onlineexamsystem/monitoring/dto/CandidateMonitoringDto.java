package com.oes.onlineexamsystem.monitoring.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateMonitoringDto {
    private Long studentId;
    private Long attemptId;
    private String studentName;
    private String registrationNumber;
    private String email;
    private String status; // "NOT_STARTED", "IN_PROGRESS", "SUBMITTED", "EXPIRED", "ABANDONED"
    private Instant startedAt;
    private Instant deadlineAt;
    private Instant lastActivityAt;
    private Boolean isOnline;
    private Integer answeredQuestions;
    private Integer totalQuestions;
    private String lastEventType;
}
