package com.oes.onlineexamsystem.examination.dto;

import com.oes.onlineexamsystem.academic.entity.CourseOffering;
import com.oes.onlineexamsystem.examination.entity.ExamStatus;
import com.oes.onlineexamsystem.examination.entity.Examination;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExaminationDto {

    private Long id;
    private String title;
    private String description;
    private String instructions;

    // Course Offering Details
    private Long courseOfferingId;
    private String courseCode;
    private String courseName;
    private String semesterName;
    private String academicYearName;
    private String section;

    // Creator / Instructor Details
    private Long createdById;
    private String createdByName;
    private String createdByEmail;

    // Exam Rules & Scoring
    private Integer durationMinutes;
    private BigDecimal totalMarks;
    private BigDecimal passingMarks;
    private Boolean randomizeQuestions;
    private Boolean randomizeOptions;
    private Integer attemptLimit;
    private Boolean negativeMarkingEnabled;
    private BigDecimal negativeMarkValue;

    private ExamStatus status;
    private Integer version;

    private Integer questionCount;
    private List<ExamQuestionDto> questions;
    private List<ExamApprovalDto> approvals;

    private Instant createdAt;
    private Instant updatedAt;

    public static ExaminationDto fromEntity(Examination e) {
        return fromEntity(e, true);
    }

    public static ExaminationDto fromEntity(Examination e, boolean includeChildren) {
        if (e == null) return null;

        ExaminationDtoBuilder builder = ExaminationDto.builder()
                .id(e.getId())
                .title(e.getTitle())
                .description(e.getDescription())
                .instructions(e.getInstructions())
                .durationMinutes(e.getDurationMinutes())
                .totalMarks(e.getTotalMarks())
                .passingMarks(e.getPassingMarks())
                .randomizeQuestions(e.getRandomizeQuestions())
                .randomizeOptions(e.getRandomizeOptions())
                .attemptLimit(e.getAttemptLimit())
                .negativeMarkingEnabled(e.getNegativeMarkingEnabled())
                .negativeMarkValue(e.getNegativeMarkValue())
                .status(e.getStatus())
                .version(e.getVersion())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt());

        if (e.getCourseOffering() != null) {
            CourseOffering co = e.getCourseOffering();
            builder.courseOfferingId(co.getId());
            builder.section(co.getSection());

            if (co.getCourse() != null) {
                builder.courseCode(co.getCourse().getCode());
                builder.courseName(co.getCourse().getName());
            }
            if (co.getSemester() != null) {
                builder.semesterName(co.getSemester().getName());
                if (co.getSemester().getAcademicYear() != null) {
                    builder.academicYearName(co.getSemester().getAcademicYear().getName());
                }
            }
        }

        if (e.getCreatedBy() != null) {
            builder.createdById(e.getCreatedBy().getId());
            builder.createdByName(e.getCreatedBy().getFirstName() + " " + e.getCreatedBy().getLastName());
            builder.createdByEmail(e.getCreatedBy().getEmail());
        }

        if (e.getExamQuestions() != null) {
            builder.questionCount(e.getExamQuestions().size());
            if (includeChildren) {
                builder.questions(e.getExamQuestions().stream()
                        .map(ExamQuestionDto::fromEntity)
                        .toList());
            }
        } else {
            builder.questionCount(0);
        }

        if (includeChildren && e.getApprovals() != null) {
            builder.approvals(e.getApprovals().stream()
                    .map(ExamApprovalDto::fromEntity)
                    .toList());
        }

        return builder.build();
    }
}
