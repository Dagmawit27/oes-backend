package com.oes.onlineexamsystem.execution.dto;

import com.oes.onlineexamsystem.execution.entity.AttemptStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamExecutionPayloadDto {
    private Long attemptId;
    private Long examinationId;
    private Long scheduleId;
    private String courseCode;
    private String courseName;
    private String examinationTitle;
    private String examinationDescription;
    private String instructions;

    private Integer durationMinutes;
    private BigDecimal totalMarks;
    private BigDecimal passingMarks;

    private Integer attemptNumber;
    private Integer attemptLimit;
    private AttemptStatus status;

    private Instant startedAt;
    private Instant deadlineAt;
    private Instant serverCurrentTime;

    private Boolean randomizeQuestions;
    private Boolean randomizeOptions;

    @Builder.Default
    private List<ExamQuestionDeliveryDto> questions = new ArrayList<>();

    @Builder.Default
    private List<StudentAnswerDto> savedAnswers = new ArrayList<>();
}
