package com.oes.onlineexamsystem.execution.entity;

import com.oes.onlineexamsystem.question.entity.QuestionOption;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "student_answer_options", uniqueConstraints = {
        @UniqueConstraint(name = "uq_answer_option", columnNames = {"student_answer_id", "question_option_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAnswerOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_answer_id", nullable = false)
    private StudentAnswer studentAnswer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_option_id", nullable = false)
    private QuestionOption questionOption;

    @CreationTimestamp
    @Column(name = "selected_at", updatable = false)
    private Instant selectedAt;
}
