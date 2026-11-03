package com.oes.onlineexamsystem.result.entity;

import com.oes.onlineexamsystem.examination.entity.ExamQuestion;
import com.oes.onlineexamsystem.execution.entity.StudentAnswer;
import com.oes.onlineexamsystem.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "grade_details", uniqueConstraints = {
        @UniqueConstraint(name = "uq_result_question_grade", columnNames = {"exam_result_id", "exam_question_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_result_id", nullable = false)
    private ExamResult examResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_question_id", nullable = false)
    private ExamQuestion examQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_answer_id")
    private StudentAnswer studentAnswer;

    @Column(name = "awarded_marks", precision = 6, scale = 2)
    private BigDecimal awardedMarks; // Nullable until graded. 0.00 is a valid earned zero mark!

    @Column(name = "maximum_marks", precision = 6, scale = 2)
    private BigDecimal maximumMarks; // Nullable if question has no assigned marks

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Builder.Default
    @Column(name = "is_auto_graded", nullable = false)
    private Boolean isAutoGraded = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
