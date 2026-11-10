package com.oes.onlineexamsystem.academic.dto;

import com.oes.onlineexamsystem.academic.entity.CourseOffering;
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
public class CourseOfferingDto {
    private Long id;

    @NotNull(message = "Course ID is required")
    private Long courseId;

    private String courseName;
    private String courseCode;

    @NotNull(message = "Semester ID is required")
    private Long semesterId;

    private String semesterName;

    private Long academicYearId;
    private String academicYearName;

    private Long programId;
    private String programName;
    private String programCode;

    private Integer studentYearLevel;

    @NotNull(message = "Instructor ID is required")
    private Long instructorId;

    private String instructorName;
    private String instructorEmployeeId;

    @NotBlank(message = "Section is required")
    private String section;

    private Integer maxStudents;
    private Long enrolledStudentsCount;
    private Boolean isActive;

    public static CourseOfferingDto fromEntity(CourseOffering co) {
        var semester = co.getSemester();
        var academicYear = semester != null ? semester.getAcademicYear() : null;
        var program = co.getProgram();

        return CourseOfferingDto.builder()
                .id(co.getId())
                .courseId(co.getCourse().getId())
                .courseName(co.getCourse().getName())
                .courseCode(co.getCourse().getCode())
                .semesterId(semester != null ? semester.getId() : null)
                .semesterName(semester != null ? semester.getName() : null)
                .academicYearId(academicYear != null ? academicYear.getId() : null)
                .academicYearName(academicYear != null ? academicYear.getName() : null)
                .programId(program != null ? program.getId() : null)
                .programName(program != null ? program.getName() : null)
                .programCode(program != null ? program.getCode() : null)
                .studentYearLevel(co.getStudentYearLevel())
                .instructorId(co.getInstructor().getId())
                .instructorName(co.getInstructor().getUser().getFullName())
                .instructorEmployeeId(co.getInstructor().getEmployeeId())
                .section(co.getSection())
                .maxStudents(co.getMaxStudents())
                .isActive(co.getIsActive())
                .build();
    }
}
