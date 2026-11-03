package com.oes.onlineexamsystem.result.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionGradingOptionDto {
    private Long id;
    private Integer optionOrder;
    private String optionText;
    private Boolean isCorrect;
    private Boolean isSelectedByStudent;
}
