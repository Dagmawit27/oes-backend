package com.oes.onlineexamsystem.academic.dto;

import com.oes.onlineexamsystem.academic.entity.Course;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDto {
    private Long id;

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    private String departmentName;

    @NotBlank(message = "Course name is required")
    private String name;

    @NotBlank(message = "Course code is required")
    private String code;

    private String description;
    private Integer credits;
    private Boolean isActive;
    private Instant createdAt;

    public static CourseDto fromEntity(Course course) {
        return CourseDto.builder()
                .id(course.getId())
                .departmentId(course.getDepartment().getId())
                .departmentName(course.getDepartment().getName())
                .name(course.getName())
                .code(course.getCode())
                .description(course.getDescription())
                .credits(course.getCredits())
                .isActive(course.getIsActive())
                .createdAt(course.getCreatedAt())
                .build();
    }
}
