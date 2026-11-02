package com.oes.onlineexamsystem.academic.dto;

import com.oes.onlineexamsystem.academic.entity.Instructor;
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
public class InstructorDto {
    private Long id;

    @NotNull(message = "User ID is required")
    private Long userId;

    private String userEmail;
    private String userFullName;

    @NotNull(message = "Department ID is required")
    private Long departmentId;

    private String departmentName;

    @NotBlank(message = "Employee ID is required")
    private String employeeId;

    private String specialization;

    public static InstructorDto fromEntity(Instructor instructor) {
        return InstructorDto.builder()
                .id(instructor.getId())
                .userId(instructor.getUser().getId())
                .userEmail(instructor.getUser().getEmail())
                .userFullName(instructor.getUser().getFullName())
                .departmentId(instructor.getDepartment().getId())
                .departmentName(instructor.getDepartment().getName())
                .employeeId(instructor.getEmployeeId())
                .specialization(instructor.getSpecialization())
                .build();
    }
}
