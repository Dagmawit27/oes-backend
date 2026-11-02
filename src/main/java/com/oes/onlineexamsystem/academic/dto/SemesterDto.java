package com.oes.onlineexamsystem.academic.dto;

import com.oes.onlineexamsystem.academic.entity.Semester;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemesterDto {
    private Long id;

    @NotNull(message = "Academic year ID is required")
    private Long academicYearId;

    private String academicYearName;

    @NotBlank(message = "Semester name is required")
    private String name;

    @NotNull(message = "Semester number is required")
    private Integer semesterNumber;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private Boolean isCurrent;

    public static SemesterDto fromEntity(Semester semester) {
        return SemesterDto.builder()
                .id(semester.getId())
                .academicYearId(semester.getAcademicYear().getId())
                .academicYearName(semester.getAcademicYear().getName())
                .name(semester.getName())
                .semesterNumber(semester.getSemesterNumber())
                .startDate(semester.getStartDate())
                .endDate(semester.getEndDate())
                .isCurrent(semester.getIsCurrent())
                .build();
    }
}
