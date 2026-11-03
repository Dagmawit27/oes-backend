package com.oes.onlineexamsystem.execution.dto;

import com.oes.onlineexamsystem.question.entity.QuestionType;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamQuestionDeliveryDto {
    private Long examQuestionId;
    private Long questionId;
    private Integer questionOrder;
    private BigDecimal marks;
    private String questionText;
    private QuestionType questionType;

    @Builder.Default
    private List<QuestionOptionDeliveryDto> options = new ArrayList<>();
}
