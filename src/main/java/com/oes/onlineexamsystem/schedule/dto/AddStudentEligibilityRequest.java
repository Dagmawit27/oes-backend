package com.oes.onlineexamsystem.schedule.dto;

import com.oes.onlineexamsystem.schedule.entity.EligibilityStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddStudentEligibilityRequest {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @Builder.Default
    private EligibilityStatus eligibilityStatus = EligibilityStatus.ELIGIBLE;

    private String reason;
}
