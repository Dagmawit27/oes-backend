package com.oes.onlineexamsystem.execution.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionOptionDeliveryDto {
    private Long id;
    private Integer optionOrder;
    private String optionText;
}
