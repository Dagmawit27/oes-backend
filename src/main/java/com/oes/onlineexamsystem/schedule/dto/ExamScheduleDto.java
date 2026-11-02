package com.oes.onlineexamsystem.schedule.dto;

import com.oes.onlineexamsystem.academic.entity.CourseOffering;
import com.oes.onlineexamsystem.examination.entity.Examination;
import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import com.oes.onlineexamsystem.schedule.entity.ScheduleStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamScheduleDto {

    private Long id;

    // Examination Details
    private Long examinationId;
    private String examinationTitle;
    private String examinationDescription;
    private BigDecimal totalMarks;
    private BigDecimal passingMarks;

    // Course Offering Details
    private Long courseOfferingId;
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String semesterName;
    private String academicYearName;
    private String section;

    // Instructor Details
    private String instructorName;
    private String instructorEmail;

    // Schedule Timing & Location
    private Instant scheduledStartAt;
    private Instant scheduledCloseAt;
    private Integer durationMinutes;

    private Long roomId;
    private String roomName;
    private String building;

    private String timezone;
    private ScheduleStatus status;
    private String instructions;

    private Long createdById;
    private String createdByName;

    private Integer eligibleStudentCount;
    private List<ExamEligibilityDto> eligibilities;

    private Instant createdAt;
    private Instant updatedAt;

    public static ExamScheduleDto fromEntity(ExamSchedule s) {
        return fromEntity(s, false);
    }

    public static ExamScheduleDto fromEntity(ExamSchedule s, boolean includeEligibilities) {
        if (s == null) return null;

        ExamScheduleDtoBuilder builder = ExamScheduleDto.builder()
                .id(s.getId())
                .scheduledStartAt(s.getScheduledStartAt())
                .scheduledCloseAt(s.getScheduledCloseAt())
                .durationMinutes(s.getDurationMinutes())
                .timezone(s.getTimezone())
                .status(s.getStatus())
                .instructions(s.getInstructions())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt());

        if (s.getRoom() != null) {
            builder.roomId(s.getRoom().getId())
                    .roomName(s.getRoom().getName())
                    .building(s.getRoom().getBuilding());
        }

        if (s.getCreatedBy() != null) {
            builder.createdById(s.getCreatedBy().getId())
                    .createdByName(s.getCreatedBy().getFirstName() + " " + s.getCreatedBy().getLastName());
        }

        if (s.getExamination() != null) {
            Examination e = s.getExamination();
            builder.examinationId(e.getId())
                    .examinationTitle(e.getTitle())
                    .examinationDescription(e.getDescription())
                    .totalMarks(e.getTotalMarks())
                    .passingMarks(e.getPassingMarks());

            if (e.getCourseOffering() != null) {
                CourseOffering co = e.getCourseOffering();
                builder.courseOfferingId(co.getId())
                        .section(co.getSection());

                if (co.getCourse() != null) {
                    builder.courseId(co.getCourse().getId())
                            .courseCode(co.getCourse().getCode())
                            .courseName(co.getCourse().getName());
                }

                if (co.getSemester() != null) {
                    builder.semesterName(co.getSemester().getName());
                    if (co.getSemester().getAcademicYear() != null) {
                        builder.academicYearName(co.getSemester().getAcademicYear().getName());
                    }
                }

                if (co.getInstructor() != null && co.getInstructor().getUser() != null) {
                    builder.instructorName(co.getInstructor().getUser().getFirstName() + " " + co.getInstructor().getUser().getLastName())
                            .instructorEmail(co.getInstructor().getUser().getEmail());
                }
            }
        }

        if (s.getEligibilities() != null) {
            builder.eligibleStudentCount(s.getEligibilities().size());
            if (includeEligibilities) {
                builder.eligibilities(s.getEligibilities().stream()
                        .map(ExamEligibilityDto::fromEntity)
                        .toList());
            }
        } else {
            builder.eligibleStudentCount(0);
        }

        return builder.build();
    }
}
