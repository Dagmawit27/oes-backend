package com.oes.onlineexamsystem.question.dto;

import com.oes.onlineexamsystem.question.entity.Question;
import com.oes.onlineexamsystem.question.entity.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Student-facing Question representation.
 * STRICT SECURITY REQUIREMENT: Never contains answer keys, correct option indicators,
 * or teacher explanation texts prior to authorized result publication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentQuestionDto {
    private Long id;
    private String questionText;
    private QuestionType questionType;
    private BigDecimal defaultMarks;

    @Builder.Default
    private List<StudentQuestionOptionDto> options = new ArrayList<>();

    public static StudentQuestionDto fromEntity(Question question) {
        List<StudentQuestionOptionDto> studentOptions = question.getOptions() != null
                ? question.getOptions().stream().map(StudentQuestionOptionDto::fromEntity).toList()
                : new ArrayList<>();

        return StudentQuestionDto.builder()
                .id(question.getId())
                .questionText(question.getQuestionText())
                .questionType(question.getQuestionType())
                .defaultMarks(question.getDefaultMarks())
                .options(studentOptions)
                .build();
    }
}
