package com.oes.onlineexamsystem.schedule.dto;

import com.oes.onlineexamsystem.schedule.entity.EligibilityStatus;
import com.oes.onlineexamsystem.schedule.entity.ExamEligibility;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamEligibilityDto {

    private Long id;
    private Long scheduleId;
    private Long studentId;
    private String studentIdNumber;
    private String studentName;
    private String studentEmail;
    private String programName;
    private EligibilityStatus eligibilityStatus;
    private String reason;
    private Instant createdAt;

    public static ExamEligibilityDto fromEntity(ExamEligibility ee) {
        if (ee == null) return null;
        ExamEligibilityDtoBuilder builder = ExamEligibilityDto.builder()
                .id(ee.getId())
                .scheduleId(ee.getSchedule() != null ? ee.getSchedule().getId() : null)
                .eligibilityStatus(ee.getEligibilityStatus())
                .reason(ee.getReason())
                .createdAt(ee.getCreatedAt());

        if (ee.getStudent() != null) {
            builder.studentId(ee.getStudent().getId())
                    .studentIdNumber(ee.getStudent().getRegistrationNumber());

            if (ee.getStudent().getUser() != null) {
                builder.studentName(ee.getStudent().getUser().getFirstName() + " " + ee.getStudent().getUser().getLastName())
                        .studentEmail(ee.getStudent().getUser().getEmail());
            }

            if (ee.getStudent().getProgram() != null) {
                builder.programName(ee.getStudent().getProgram().getName());
            }
        }

        return builder.build();
    }
}
