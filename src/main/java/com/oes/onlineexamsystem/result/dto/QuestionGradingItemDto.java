package com.oes.onlineexamsystem.result.dto;

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
public class QuestionGradingItemDto {
    private Long gradeDetailId;
    private Long examQuestionId;
    private Integer questionOrder;
    private QuestionType questionType;
    private String questionText;
    private BigDecimal maximumMarks;
    private BigDecimal awardedMarks; // Nullable when ungraded
    private String feedback;
    private Boolean isAutoGraded;
    private String studentAnswerText;

    @Builder.Default
    private List<Long> studentSelectedOptionIds = new ArrayList<>();

    @Builder.Default
    private List<QuestionGradingOptionDto> options = new ArrayList<>();
}
