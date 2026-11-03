package com.oes.onlineexamsystem.result.entity;

import com.oes.onlineexamsystem.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "result_correction_audits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultCorrectionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_result_id", nullable = false)
    private ExamResult examResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corrected_by", nullable = false)
    private User correctedBy;

    @Column(name = "previous_earned_marks", precision = 7, scale = 2)
    private BigDecimal previousEarnedMarks;

    @Column(name = "new_earned_marks", precision = 7, scale = 2, nullable = false)
    private BigDecimal newEarnedMarks;

    @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "corrected_at", nullable = false)
    private Instant correctedAt;
}
