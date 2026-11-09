package com.oes.onlineexamsystem.execution.entity;

import com.oes.onlineexamsystem.academic.entity.Student;
import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "attempt_passcode_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttemptPasscodeAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private ExamSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "attempt_type", length = 30, nullable = false)
    private String attemptType; // START, RESUME_1, RESUME_2_PLUS

    @Builder.Default
    @Column(name = "is_successful", nullable = false)
    private Boolean isSuccessful = false;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "attempted_at", updatable = false)
    private Instant attemptedAt;
}
