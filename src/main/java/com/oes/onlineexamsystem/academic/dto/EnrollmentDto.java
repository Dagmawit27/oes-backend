package com.oes.onlineexamsystem.academic.dto;

import com.oes.onlineexamsystem.academic.entity.Enrollment;
import com.oes.onlineexamsystem.academic.entity.EnrollmentStatus;
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
public class EnrollmentDto {
    private Long id;

    @NotNull(message = "Student ID is required")
    private Long studentId;

    private String studentName;
    private String studentRegistrationNumber;
    private String studentEmail;

    @NotNull(message = "Course assignment ID is required")
    private Long courseOfferingId;

    private String courseName;
    private String courseCode;
    private String semesterName;
    private String instructorName;
    private String section;

    private LocalDate enrolledDate;
    private EnrollmentStatus status;

    public static EnrollmentDto fromEntity(Enrollment enrollment) {
        return EnrollmentDto.builder()
                .id(enrollment.getId())
                .studentId(enrollment.getStudent().getId())
                .studentName(enrollment.getStudent().getUser().getFullName())
                .studentRegistrationNumber(enrollment.getStudent().getRegistrationNumber())
                .studentEmail(enrollment.getStudent().getUser().getEmail())
                .courseOfferingId(enrollment.getCourseOffering().getId())
                .courseName(enrollment.getCourseOffering().getCourse().getName())
                .courseCode(enrollment.getCourseOffering().getCourse().getCode())
                .semesterName(enrollment.getCourseOffering().getSemester().getName())
                .instructorName(enrollment.getCourseOffering().getInstructor().getUser().getFullName())
                .section(enrollment.getCourseOffering().getSection())
                .enrolledDate(enrollment.getEnrolledDate())
                .status(enrollment.getStatus())
                .build();
    }
}
