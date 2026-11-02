package com.oes.onlineexamsystem.question.dto;

import com.oes.onlineexamsystem.question.entity.QuestionOption;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOptionDto {
    private Long id;

    @NotBlank(message = "Option text is required")
    private String optionText;

    private Integer optionOrder;
    private Boolean isCorrect;
    private String explanation;

    public static QuestionOptionDto fromEntity(QuestionOption option) {
        return QuestionOptionDto.builder()
                .id(option.getId())
                .optionText(option.getOptionText())
                .optionOrder(option.getOptionOrder())
                .isCorrect(option.getIsCorrect())
                .explanation(option.getExplanation())
                .build();
    }
}
