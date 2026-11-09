package com.oes.onlineexamsystem.result.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentGradeDetailDto {
    private Integer questionOrder;
    private Long examQuestionId;
    private String questionText;
    private String questionType;
    private Boolean isAutoGraded;
    private BigDecimal awardedMarks;
    private BigDecimal maximumMarks;
    private String feedback;
    private String studentAnswerText;
    private List<QuestionGradingOptionDto> options;
}
