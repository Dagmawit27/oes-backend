package com.oes.onlineexamsystem.question.dto;

import com.oes.onlineexamsystem.question.entity.QuestionCategory;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionCategoryDto {
    private Long id;
    private Long courseId;
    private String courseCode;
    private String courseName;

    @NotBlank(message = "Category name is required")
    private String name;

    @NotBlank(message = "Category code is required")
    private String code;

    private String description;
    private Boolean isActive;

    public static QuestionCategoryDto fromEntity(QuestionCategory category) {
        return QuestionCategoryDto.builder()
                .id(category.getId())
                .courseId(category.getCourse() != null ? category.getCourse().getId() : null)
                .courseCode(category.getCourse() != null ? category.getCourse().getCode() : null)
                .courseName(category.getCourse() != null ? category.getCourse().getName() : null)
                .name(category.getName())
                .code(category.getCode())
                .description(category.getDescription())
                .isActive(category.getIsActive())
                .build();
    }
}
