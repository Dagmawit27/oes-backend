package com.oes.onlineexamsystem.examination.entity;

import com.oes.onlineexamsystem.academic.entity.CourseOffering;
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
@Table(name = "examinations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Examination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false)
    private CourseOffering courseOffering;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "total_marks", precision = 6, scale = 2, nullable = false)
    private BigDecimal totalMarks;

    @Column(name = "passing_marks", precision = 6, scale = 2, nullable = false)
    private BigDecimal passingMarks;

    @Builder.Default
    @Column(name = "randomize_questions", nullable = false)
    private Boolean randomizeQuestions = false;

    @Builder.Default
    @Column(name = "randomize_options", nullable = false)
    private Boolean randomizeOptions = false;

    @Builder.Default
    @Column(name = "attempt_limit", nullable = false)
    private Integer attemptLimit = 1;

    @Builder.Default
    @Column(name = "negative_marking_enabled", nullable = false)
    private Boolean negativeMarkingEnabled = false;

    @Builder.Default
    @Column(name = "negative_mark_value", precision = 4, scale = 2)
    private BigDecimal negativeMarkValue = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ExamStatus status = ExamStatus.DRAFT;

    @Builder.Default
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Builder.Default
    @OneToMany(mappedBy = "examination", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("questionOrder ASC")
    private List<ExamQuestion> examQuestions = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "examination", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("reviewedAt DESC")
    private List<ExamApproval> approvals = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
