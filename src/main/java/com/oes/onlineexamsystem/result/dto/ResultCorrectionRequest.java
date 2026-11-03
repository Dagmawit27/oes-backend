package com.oes.onlineexamsystem.result.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultCorrectionRequest {

    @NotNull(message = "New earned marks value is required")
    @DecimalMin(value = "0.0", message = "Earned marks cannot be negative")
    private BigDecimal newEarnedMarks;

    @NotBlank(message = "Audit reason is required for correcting a published result")
    private String reason;
}
