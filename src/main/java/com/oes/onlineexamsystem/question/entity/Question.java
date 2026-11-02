package com.oes.onlineexamsystem.question.entity;

import com.oes.onlineexamsystem.academic.entity.Course;
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
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private QuestionBank bank;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private QuestionCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "question_text", columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", length = 50, nullable = false)
    private QuestionType questionType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", length = 20, nullable = false)
    private DifficultyLevel difficultyLevel = DifficultyLevel.MEDIUM;

    @Builder.Default
    @Column(name = "default_marks", precision = 5, scale = 2, nullable = false)
    private BigDecimal defaultMarks = BigDecimal.valueOf(1.00);

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Builder.Default
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder.Default
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("optionOrder ASC")
    private List<QuestionOption> options = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
