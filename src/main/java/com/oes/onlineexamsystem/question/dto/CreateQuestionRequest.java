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
public class CreateQuestionRequest {

    private Long bankId;

    @NotNull(message = "Course ID is required")
    private Long courseId;

    private Long categoryId;

    @NotBlank(message = "Question text is required")
    private String questionText;

    @NotNull(message = "Question type is required")
    private QuestionType questionType;

    @Builder.Default
    @NotNull(message = "Difficulty level is required")
    private DifficultyLevel difficultyLevel = DifficultyLevel.MEDIUM;

    @Builder.Default
    @NotNull(message = "Default marks are required")
    @DecimalMin(value = "0.25", message = "Marks must be at least 0.25")
    private BigDecimal defaultMarks = BigDecimal.valueOf(1.00);

    private String explanation;

    @Builder.Default
    private List<QuestionOptionDto> options = new ArrayList<>();
}
