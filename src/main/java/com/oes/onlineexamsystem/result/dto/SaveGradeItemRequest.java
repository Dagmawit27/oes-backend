package com.oes.onlineexamsystem.result.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveGradeItemRequest {

    @NotNull(message = "Exam question ID is required")
    private Long examQuestionId;

    @DecimalMin(value = "0.0", message = "Awarded marks cannot be negative")
    private BigDecimal awardedMarks; // Nullable if left ungraded

    private String feedback;
}
