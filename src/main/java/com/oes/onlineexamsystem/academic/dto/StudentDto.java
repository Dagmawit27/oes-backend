package com.oes.onlineexamsystem.academic.dto;

import com.oes.onlineexamsystem.academic.entity.Student;
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
public class StudentDto {
    private Long id;

    @NotNull(message = "User ID is required")
    private Long userId;

    private String userEmail;
    private String userFullName;

    @NotNull(message = "Program ID is required")
    private Long programId;

    private String programName;
    private String programCode;

    @NotBlank(message = "Registration number is required")
    private String registrationNumber;

    private Integer yearOfStudy;

    public static StudentDto fromEntity(Student student) {
        return StudentDto.builder()
                .id(student.getId())
                .userId(student.getUser().getId())
                .userEmail(student.getUser().getEmail())
                .userFullName(student.getUser().getFullName())
                .programId(student.getProgram().getId())
                .programName(student.getProgram().getName())
                .programCode(student.getProgram().getCode())
                .registrationNumber(student.getRegistrationNumber())
                .yearOfStudy(student.getYearOfStudy())
                .build();
    }
}
