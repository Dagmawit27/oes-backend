package com.oes.onlineexamsystem.result.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentGradeDetailDto {
    private Integer questionOrder;
    private String questionText;
    private BigDecimal awardedMarks;
    private BigDecimal maximumMarks;
    private String feedback;
}
