package com.oes.onlineexamsystem.execution.dto;

import com.oes.onlineexamsystem.execution.entity.AttemptStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamSubmissionResultDto {
    private Long attemptId;
    private Long examinationId;
    private String examinationTitle;
    private AttemptStatus status;
    private Instant startedAt;
    private Instant submittedAt;
    private Integer totalQuestions;
    private Integer answeredQuestions;
    private String message;
}
