package com.oes.onlineexamsystem.question.dto;

import com.oes.onlineexamsystem.question.entity.QuestionOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Student-facing Question Option representation.
 * STRICT SECURITY REQUIREMENT: Never contains isCorrect or internal explanations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentQuestionOptionDto {
    private Long id;
    private String optionText;
    private Integer optionOrder;

    public static StudentQuestionOptionDto fromEntity(QuestionOption option) {
        return StudentQuestionOptionDto.builder()
                .id(option.getId())
                .optionText(option.getOptionText())
                .optionOrder(option.getOptionOrder())
                .build();
    }
}
