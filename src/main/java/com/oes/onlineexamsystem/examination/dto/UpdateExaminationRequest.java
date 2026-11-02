package com.oes.onlineexamsystem.examination.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateExaminationRequest {

    @NotBlank(message = "Examination title is required")
    private String title;

    private String description;

    private String instructions;

    @NotNull(message = "Duration in minutes is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer durationMinutes;

    @NotNull(message = "Total marks is required")
    @DecimalMin(value = "0.01", message = "Total marks must be greater than zero")
    private BigDecimal totalMarks;

    @NotNull(message = "Passing marks is required")
    @DecimalMin(value = "0.00", message = "Passing marks cannot be negative")
    private BigDecimal passingMarks;

    @Builder.Default
    private Boolean randomizeQuestions = false;

    @Builder.Default
    private Boolean randomizeOptions = false;

    @Builder.Default
    @Min(value = 1, message = "Attempt limit must be at least 1")
    private Integer attemptLimit = 1;

    @Builder.Default
    private Boolean negativeMarkingEnabled = false;

    @Builder.Default
    private BigDecimal negativeMarkValue = BigDecimal.ZERO;

    @NotEmpty(message = "At least one question must be selected for the examination")
    @Valid
    private List<SelectedQuestionRequest> questions;
}
