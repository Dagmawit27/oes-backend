package com.oes.onlineexamsystem.examination.entity;

import com.oes.onlineexamsystem.question.entity.Question;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "exam_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "examination_id", nullable = false)
    private Examination examination;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Builder.Default
    @Column(name = "question_order", nullable = false)
    private Integer questionOrder = 1;

    @Column(name = "marks", precision = 5, scale = 2, nullable = false)
    private BigDecimal marks;

    @Builder.Default
    @Column(name = "question_version", nullable = false)
    private Integer questionVersion = 1;

    @Column(name = "question_snapshot", columnDefinition = "TEXT")
    private String questionSnapshot;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
