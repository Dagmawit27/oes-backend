package com.oes.onlineexamsystem.question.dto;

import com.oes.onlineexamsystem.question.entity.DifficultyLevel;
import com.oes.onlineexamsystem.question.entity.QuestionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuestionRequest {

    private Long bankId;
    private Long categoryId;

    @NotBlank(message = "Question text is required")
    private String questionText;

    @NotNull(message = "Question type is required")
    private QuestionType questionType;

    @NotNull(message = "Difficulty level is required")
    private DifficultyLevel difficultyLevel;

    @NotNull(message = "Default marks are required")
    @DecimalMin(value = "0.25", message = "Marks must be at least 0.25")
    private BigDecimal defaultMarks;

    private String explanation;
    private Boolean isActive;

    @Builder.Default
    private List<QuestionOptionDto> options = new ArrayList<>();
}
