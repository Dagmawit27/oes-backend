package com.oes.onlineexamsystem.result.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentExamResultDto {
    private Long resultId;
    private Long examinationId;
    private String examinationTitle;
    private String attemptStatus; // NOT_ATTEMPTED, IN_PROGRESS, SUBMITTED, EXPIRED
    private String displayStatus; // Not Attempted, Pending, Published
    private BigDecimal earnedMarks;
    private BigDecimal totalMarks;
    private String marksDisplay; // e.g. "32.00 / 40.00", "Not Attempted", "Pending"
    private Instant publishedAt;

    @Builder.Default
    private List<StudentGradeDetailDto> details = new ArrayList<>();
}
