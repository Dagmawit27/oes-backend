package com.oes.onlineexamsystem.examination.dto;

import com.oes.onlineexamsystem.examination.entity.ExamQuestion;
import com.oes.onlineexamsystem.question.dto.QuestionOptionDto;
import com.oes.onlineexamsystem.question.entity.QuestionType;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamQuestionDto {

    private Long id;
    private Long examinationId;
    private Long questionId;
    private Integer questionOrder;
    private BigDecimal marks;
    private Integer questionVersion;
    private String questionSnapshot;

    // Rich question properties for instructor/officer preview
    private String questionText;
    private QuestionType questionType;
    private String explanation;
    private List<QuestionOptionDto> options;
    private Instant createdAt;

    public static ExamQuestionDto fromEntity(ExamQuestion eq) {
        if (eq == null) return null;
        ExamQuestionDtoBuilder builder = ExamQuestionDto.builder()
                .id(eq.getId())
                .examinationId(eq.getExamination() != null ? eq.getExamination().getId() : null)
                .questionId(eq.getQuestion() != null ? eq.getQuestion().getId() : null)
                .questionOrder(eq.getQuestionOrder())
                .marks(eq.getMarks())
                .questionVersion(eq.getQuestionVersion())
                .questionSnapshot(eq.getQuestionSnapshot())
                .createdAt(eq.getCreatedAt());

        if (eq.getQuestion() != null) {
            builder.questionText(eq.getQuestion().getQuestionText())
                    .questionType(eq.getQuestion().getQuestionType())
                    .explanation(eq.getQuestion().getExplanation());

            if (eq.getQuestion().getOptions() != null) {
                builder.options(eq.getQuestion().getOptions().stream()
                        .map(QuestionOptionDto::fromEntity)
                        .toList());
            }
        }
        return builder.build();
    }
}
