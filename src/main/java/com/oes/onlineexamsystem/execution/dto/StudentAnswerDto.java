package com.oes.onlineexamsystem.execution.dto;

import com.oes.onlineexamsystem.execution.entity.StudentAnswer;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAnswerDto {
    private Long id;
    private Long attemptId;
    private Long examQuestionId;
    private String answerText;
    private Boolean isFlagged;

    @Builder.Default
    private List<Long> selectedOptionIds = new ArrayList<>();

    private Instant savedAt;
    private Long version;

    public static StudentAnswerDto fromEntity(StudentAnswer answer) {
        if (answer == null) return null;

        List<Long> optionIds = answer.getSelectedOptions() != null
                ? answer.getSelectedOptions().stream().map(o -> o.getQuestionOption().getId()).toList()
                : new ArrayList<>();

        return StudentAnswerDto.builder()
                .id(answer.getId())
                .attemptId(answer.getAttempt() != null ? answer.getAttempt().getId() : null)
                .examQuestionId(answer.getExamQuestion() != null ? answer.getExamQuestion().getId() : null)
                .answerText(answer.getAnswerText())
                .isFlagged(answer.getIsFlagged())
                .selectedOptionIds(optionIds)
                .savedAt(answer.getSavedAt())
                .version(answer.getVersion())
                .build();
    }
}
