package com.oes.onlineexamsystem.examination.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectedQuestionRequest {

    @NotNull(message = "Question ID is required")
    private Long questionId;

    private Integer questionOrder;

    @NotNull(message = "Marks for question is required")
    @DecimalMin(value = "0.01", message = "Marks must be greater than zero")
    private BigDecimal marks;
}
