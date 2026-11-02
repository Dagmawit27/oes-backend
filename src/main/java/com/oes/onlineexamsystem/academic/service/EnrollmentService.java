package com.oes.onlineexamsystem.academic.service;

import com.oes.onlineexamsystem.academic.dto.EnrollmentDto;
import com.oes.onlineexamsystem.academic.entity.*;
import com.oes.onlineexamsystem.academic.repository.*;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseOfferingRepository courseOfferingRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             StudentRepository studentRepository,
                             CourseOfferingRepository courseOfferingRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.courseOfferingRepository = courseOfferingRepository;
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDto> getAllEnrollments() {
        return enrollmentRepository.findAll().stream()
                .map(EnrollmentDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDto> getEnrollmentsByOffering(Long offeringId) {
        return enrollmentRepository.findByCourseOfferingId(offeringId).stream()
                .map(EnrollmentDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDto> getEnrollmentsByStudentUserId(Long userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "userId", userId));

        return enrollmentRepository.findByStudentId(student.getId()).stream()
                .map(EnrollmentDto::fromEntity)
                .toList();
    }

    @Transactional
    public EnrollmentDto enrollStudent(EnrollmentDto dto) {
        // Enforce Business Rule: prevent duplicate student-course offering assignments
        if (enrollmentRepository.existsByStudentIdAndCourseOfferingId(dto.getStudentId(), dto.getCourseOfferingId())) {
            throw new DuplicateResourceException("Enrollment", "student and course offering",
                    "Student ID " + dto.getStudentId() + " is already enrolled in Offering ID " + dto.getCourseOfferingId());
        }

        Student student = studentRepository.findById(dto.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student", "id", dto.getStudentId()));

        CourseOffering offering = courseOfferingRepository.findById(dto.getCourseOfferingId())
                .orElseThrow(() -> new ResourceNotFoundException("CourseOffering", "id", dto.getCourseOfferingId()));

        if (!Boolean.TRUE.equals(offering.getIsActive())) {
            throw new BusinessRuleException("Cannot enroll in an inactive course offering");
        }

        long activeCount = enrollmentRepository.countByCourseOfferingIdAndStatus(offering.getId(), EnrollmentStatus.ACTIVE);
        if (activeCount >= offering.getMaxStudents()) {
            throw new BusinessRuleException("Course offering capacity has been reached (Max: " + offering.getMaxStudents() + ")");
        }

        Enrollment enrollment = Enrollment.builder()
                .student(student)
                .courseOffering(offering)
                .enrolledDate(LocalDate.now())
                .status(EnrollmentStatus.ACTIVE)
                .build();

        return EnrollmentDto.fromEntity(enrollmentRepository.save(enrollment));
    }

    @Transactional
    public void dropEnrollment(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", "id", enrollmentId));

        enrollment.setStatus(EnrollmentStatus.DROPPED);
        enrollmentRepository.save(enrollment);
    }
}
