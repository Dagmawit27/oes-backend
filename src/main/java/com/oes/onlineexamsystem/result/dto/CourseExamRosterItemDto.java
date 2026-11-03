package com.oes.onlineexamsystem.result.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseExamRosterItemDto {
    private Long studentId;
    private String registrationNumber;
    private String studentName;
    private String courseTitle;
    private String examinationTitle;
    private Long attemptId;
    private Long resultId;
    private String attemptStatus; // Not Attempted, In Progress, Submitted, Expired
    private String gradingStatus; // Pending, In Progress, Grading Completed
    private String publicationStatus; // Draft, Published
    private BigDecimal earnedMarks;
    private BigDecimal totalMarks;
    private String marksDisplay; // e.g. "32.00 / 40.00" or "Not Attempted"
    private String gradedBy;
    private Instant gradedAt;
    private String publishedBy;
    private Instant publishedAt;
}
