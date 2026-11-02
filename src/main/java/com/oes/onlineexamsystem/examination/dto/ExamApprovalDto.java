package com.oes.onlineexamsystem.examination.dto;

import com.oes.onlineexamsystem.examination.entity.ApprovalDecision;
import com.oes.onlineexamsystem.examination.entity.ExamApproval;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamApprovalDto {

    private Long id;
    private Long examinationId;
    private Long reviewerId;
    private String reviewerName;
    private String reviewerEmail;
    private ApprovalDecision decision;
    private String comments;
    private Instant reviewedAt;

    public static ExamApprovalDto fromEntity(ExamApproval approval) {
        if (approval == null) return null;
        return ExamApprovalDto.builder()
                .id(approval.getId())
                .examinationId(approval.getExamination() != null ? approval.getExamination().getId() : null)
                .reviewerId(approval.getReviewer() != null ? approval.getReviewer().getId() : null)
                .reviewerName(approval.getReviewer() != null ? approval.getReviewer().getFirstName() + " " + approval.getReviewer().getLastName() : null)
                .reviewerEmail(approval.getReviewer() != null ? approval.getReviewer().getEmail() : null)
                .decision(approval.getDecision())
                .comments(approval.getComments())
                .reviewedAt(approval.getReviewedAt())
                .build();
    }
}
