package com.oes.onlineexamsystem.result.entity;

import com.oes.onlineexamsystem.academic.entity.Student;
import com.oes.onlineexamsystem.examination.entity.Examination;
import com.oes.onlineexamsystem.execution.entity.ExamAttempt;
import com.oes.onlineexamsystem.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exam_results", uniqueConstraints = {
        @UniqueConstraint(name = "uq_exam_student_result", columnNames = {"examination_id", "student_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private ExamAttempt attempt;

    @Column(name = "earned_marks", precision = 7, scale = 2)
    private BigDecimal earnedMarks; // Nullable when grading is incomplete/unavailable

    @Column(name = "total_marks", precision = 7, scale = 2, nullable = false)
    private BigDecimal totalMarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_status", length = 30, nullable = false)
    @Builder.Default
    private GradingStatus gradingStatus = GradingStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", length = 30, nullable = false)
    @Builder.Default
    private PublicationStatus publicationStatus = PublicationStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by")
    private User publishedBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "examResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<GradeDetail> gradeDetails = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
