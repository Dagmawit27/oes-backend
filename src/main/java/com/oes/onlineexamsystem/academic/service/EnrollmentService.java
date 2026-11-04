package com.oes.onlineexamsystem.academic.service;

import com.oes.onlineexamsystem.academic.dto.EnrollmentDto;
import com.oes.onlineexamsystem.academic.entity.*;
import com.oes.onlineexamsystem.academic.repository.*;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.user.entity.RoleName;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final InstructorRepository instructorRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             StudentRepository studentRepository,
                             CourseOfferingRepository courseOfferingRepository,
                             UserRepository userRepository,
                             ProgramRepository programRepository,
                             InstructorRepository instructorRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.userRepository = userRepository;
        this.programRepository = programRepository;
        this.instructorRepository = instructorRepository;
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
    public List<EnrollmentDto> getEnrollmentsForInstructor(Long userId) {
        Instructor instructor = instructorRepository.findByUserId(userId).orElse(null);
        if (instructor == null) {
            return List.of();
        }
        List<CourseOffering> offerings = courseOfferingRepository.findByInstructorId(instructor.getId());
        return offerings.stream()
                .flatMap(o -> enrollmentRepository.findByCourseOfferingId(o.getId()).stream())
                .map(EnrollmentDto::fromEntity)
                .toList();
    }

    @Transactional
    public List<EnrollmentDto> getEnrollmentsByStudentUserId(Long userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User u = userRepository.findById(userId).orElse(null);
                    if (u != null && u.getRoles() != null && u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ROLE_STUDENT)) {
                        List<Program> progs = programRepository.findAll();
                        if (!progs.isEmpty()) {
                            String regNo = "STU-" + String.format("%04d", u.getId());
                            if (studentRepository.existsByRegistrationNumberIgnoreCase(regNo)) {
                                regNo = "STU-" + u.getId() + "-" + (System.currentTimeMillis() % 1000);
                            }
                            Student newStudent = Student.builder()
                                    .user(u)
                                    .program(progs.get(0))
                                    .registrationNumber(regNo)
                                    .yearOfStudy(1)
                                    .build();
                            return studentRepository.save(newStudent);
                        }
                    }
                    throw new ResourceNotFoundException("Student", "userId", userId);
                });

        return enrollmentRepository.findByStudentId(student.getId()).stream()
                .map(EnrollmentDto::fromEntity)
                .toList();
    }

    @Transactional
    public EnrollmentDto enrollStudent(EnrollmentDto dto) {
        // Resolve student by student.id OR user.id
        Student student = studentRepository.findById(dto.getStudentId())
                .or(() -> studentRepository.findByUserId(dto.getStudentId()))
                .orElseGet(() -> {
                    User u = userRepository.findById(dto.getStudentId()).orElse(null);
                    if (u != null && u.getRoles() != null && u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ROLE_STUDENT)) {
                        List<Program> progs = programRepository.findAll();
                        if (!progs.isEmpty()) {
                            String regNo = "STU-" + String.format("%04d", u.getId());
                            if (studentRepository.existsByRegistrationNumberIgnoreCase(regNo)) {
                                regNo = "STU-" + u.getId() + "-" + (System.currentTimeMillis() % 1000);
                            }
                            Student newStudent = Student.builder()
                                    .user(u)
                                    .program(progs.get(0))
                                    .registrationNumber(regNo)
                                    .yearOfStudy(1)
                                    .build();
                            return studentRepository.save(newStudent);
                        }
                    }
                    throw new ResourceNotFoundException("Student", "id", dto.getStudentId());
                });

        // Enforce Business Rule: prevent duplicate student-course offering assignments
        if (enrollmentRepository.existsByStudentIdAndCourseOfferingId(student.getId(), dto.getCourseOfferingId())) {
            throw new DuplicateResourceException("Enrollment", "student and course offering",
                    "Student " + student.getRegistrationNumber() + " is already enrolled in Offering ID " + dto.getCourseOfferingId());
        }

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
