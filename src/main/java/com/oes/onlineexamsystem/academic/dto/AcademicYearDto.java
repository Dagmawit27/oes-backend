package com.oes.onlineexamsystem.academic.dto;

import com.oes.onlineexamsystem.academic.entity.AcademicYear;
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
public class AcademicYearDto {
    private Long id;

    @NotBlank(message = "Academic year name is required")
    private String name;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private Boolean isCurrent;

    public static AcademicYearDto fromEntity(AcademicYear year) {
        return AcademicYearDto.builder()
                .id(year.getId())
                .name(year.getName())
                .startDate(year.getStartDate())
                .endDate(year.getEndDate())
                .isCurrent(year.getIsCurrent())
                .build();
    }
}
