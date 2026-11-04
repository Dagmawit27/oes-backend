package com.oes.onlineexamsystem.question.dto;

import com.oes.onlineexamsystem.question.entity.Question;
import com.oes.onlineexamsystem.question.entity.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {
    private Long id;
    private Long bankId;
    private String bankName;

    private Long courseId;
    private String courseCode;
    private String courseName;

    private Long categoryId;
    private String categoryName;
    private String categoryCode;

    private Long createdById;
    private String createdByName;

    private String questionText;
    private QuestionType questionType;
    private BigDecimal defaultMarks;
    private String explanation;
    private Integer version;
    private Boolean isActive;

    @Builder.Default
    private List<QuestionOptionDto> options = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    public static QuestionDto fromEntity(Question question) {
        List<QuestionOptionDto> optionDtos = question.getOptions() != null
                ? question.getOptions().stream().map(QuestionOptionDto::fromEntity).toList()
                : new ArrayList<>();

        return QuestionDto.builder()
                .id(question.getId())
                .bankId(question.getBank() != null ? question.getBank().getId() : null)
                .bankName(question.getBank() != null ? question.getBank().getName() : null)
                .courseId(question.getCourse().getId())
                .courseCode(question.getCourse().getCode())
                .courseName(question.getCourse().getName())
                .categoryId(question.getCategory() != null ? question.getCategory().getId() : null)
                .categoryName(question.getCategory() != null ? question.getCategory().getName() : null)
                .categoryCode(question.getCategory() != null ? question.getCategory().getCode() : null)
                .createdById(question.getCreatedBy().getId())
                .createdByName(question.getCreatedBy().getFullName())
                .questionText(question.getQuestionText())
                .questionType(question.getQuestionType())
                .defaultMarks(question.getDefaultMarks())
                .explanation(question.getExplanation())
                .version(question.getVersion())
                .isActive(question.getIsActive())
                .options(optionDtos)
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .build();
    }
}
