package com.oes.onlineexamsystem.result.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentCourseResultsDto {
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String departmentName;
    private String semesterName;

    @Builder.Default
    private List<StudentExamResultDto> examinations = new ArrayList<>();
}
