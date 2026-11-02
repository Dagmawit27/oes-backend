package com.oes.onlineexamsystem.schedule.dto;

import com.oes.onlineexamsystem.schedule.entity.EligibilityStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEligibilityRequest {

    @NotNull(message = "Eligibility status is required")
    private EligibilityStatus eligibilityStatus;

    private String reason;
}
