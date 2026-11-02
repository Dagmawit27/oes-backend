package com.oes.onlineexamsystem.examination.dto;

import com.oes.onlineexamsystem.examination.entity.ApprovalDecision;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewExaminationRequest {

    @NotNull(message = "Approval decision is required")
    private ApprovalDecision decision;

    private String comments;
}
