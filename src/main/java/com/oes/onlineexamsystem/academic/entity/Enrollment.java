package com.oes.onlineexamsystem.academic.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "enrollments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_id", "course_offering_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_offering_id", nullable = false)
    private CourseOffering courseOffering;

    @Builder.Default
    @Column(name = "enrolled_date", nullable = false)
    private LocalDate enrolledDate = LocalDate.now();

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
