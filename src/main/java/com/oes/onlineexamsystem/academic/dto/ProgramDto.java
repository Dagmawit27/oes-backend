package com.oes.onlineexamsystem.academic.dto;

import com.oes.onlineexamsystem.academic.entity.Program;
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
public class ProgramDto {
    private Long id;

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    private String departmentName;

    @NotBlank(message = "Program name is required")
    private String name;

    @NotBlank(message = "Program code is required")
    private String code;

    @NotBlank(message = "Degree type is required")
    private String degreeType;

    private Integer durationYears;
    private Boolean isActive;
    private Instant createdAt;

    public static ProgramDto fromEntity(Program program) {
        return ProgramDto.builder()
                .id(program.getId())
                .departmentId(program.getDepartment().getId())
                .departmentName(program.getDepartment().getName())
                .name(program.getName())
                .code(program.getCode())
                .degreeType(program.getDegreeType())
                .durationYears(program.getDurationYears())
                .isActive(program.getIsActive())
                .createdAt(program.getCreatedAt())
                .build();
    }
}
