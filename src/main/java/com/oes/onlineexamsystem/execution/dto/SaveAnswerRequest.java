package com.oes.onlineexamsystem.execution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveAnswerRequest {

    @NotNull(message = "Exam question ID is required")
    private Long examQuestionId;

    private List<Long> selectedOptionIds;

    private String answerText;

    private Boolean isFlagged;

    private Long clientVersion;
}
