package com.oes.onlineexamsystem.question.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "question_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "option_text", columnDefinition = "TEXT", nullable = false)
    private String optionText;

    @Builder.Default
    @Column(name = "option_order", nullable = false)
    private Integer optionOrder = 1;

    @Builder.Default
    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect = false;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
