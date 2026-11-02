package com.oes.onlineexamsystem.question.dto;

import com.oes.onlineexamsystem.question.entity.QuestionBank;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionBankDto {
    private Long id;

    @NotNull(message = "Course ID is required")
    private Long courseId;
    private String courseCode;
    private String courseName;

    private Long createdById;
    private String createdByName;

    @NotBlank(message = "Bank name is required")
    private String name;

    private String description;
    private Boolean isActive;
    private Long questionCount;

    public static QuestionBankDto fromEntity(QuestionBank bank) {
        return QuestionBankDto.builder()
                .id(bank.getId())
                .courseId(bank.getCourse().getId())
                .courseCode(bank.getCourse().getCode())
                .courseName(bank.getCourse().getName())
                .createdById(bank.getCreatedBy().getId())
                .createdByName(bank.getCreatedBy().getFullName())
                .name(bank.getName())
                .description(bank.getDescription())
                .isActive(bank.getIsActive())
                .build();
    }
}
