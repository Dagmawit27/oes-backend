package com.oes.onlineexamsystem.academic.service;

import com.oes.onlineexamsystem.academic.dto.CourseOfferingDto;
import com.oes.onlineexamsystem.academic.entity.*;
import com.oes.onlineexamsystem.academic.repository.*;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseOfferingService {

    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseRepository courseRepository;
    private final SemesterRepository semesterRepository;
    private final InstructorRepository instructorRepository;
    private final EnrollmentRepository enrollmentRepository;

    public CourseOfferingService(CourseOfferingRepository courseOfferingRepository,
                                 CourseRepository courseRepository,
                                 SemesterRepository semesterRepository,
                                 InstructorRepository instructorRepository,
                                 EnrollmentRepository enrollmentRepository) {
        this.courseOfferingRepository = courseOfferingRepository;
        this.courseRepository = courseRepository;
        this.semesterRepository = semesterRepository;
        this.instructorRepository = instructorRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseOfferingDto> getAllOfferings() {
        return courseOfferingRepository.findAll().stream()
                .map(this::mapOfferingWithCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseOfferingDto getOfferingById(Long id) {
        CourseOffering offering = courseOfferingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseOffering", "id", id));
        return mapOfferingWithCount(offering);
    }

    @Transactional(readOnly = true)
    public List<CourseOfferingDto> getOfferingsBySemester(Long semesterId) {
        return courseOfferingRepository.findBySemesterId(semesterId).stream()
                .map(this::mapOfferingWithCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CourseOfferingDto> getOfferingsByInstructorUserId(Long userId) {
        Instructor instructor = instructorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", "userId", userId));

        return courseOfferingRepository.findByInstructorId(instructor.getId()).stream()
                .map(this::mapOfferingWithCount)
                .toList();
    }

    @Transactional
    public CourseOfferingDto createOffering(CourseOfferingDto dto) {
        if (courseOfferingRepository.existsByCourseIdAndSemesterIdAndSection(
                dto.getCourseId(), dto.getSemesterId(), dto.getSection().trim().toUpperCase())) {
            throw new DuplicateResourceException("CourseOffering", "course, semester, and section",
                    dto.getCourseId() + " in section " + dto.getSection());
        }

        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", dto.getCourseId()));

        Semester semester = semesterRepository.findById(dto.getSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", dto.getSemesterId()));

        Instructor instructor = instructorRepository.findById(dto.getInstructorId())
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", "id", dto.getInstructorId()));

        CourseOffering offering = CourseOffering.builder()
                .course(course)
                .semester(semester)
                .instructor(instructor)
                .section(dto.getSection().trim().toUpperCase())
                .maxStudents(dto.getMaxStudents() != null ? dto.getMaxStudents() : 50)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return mapOfferingWithCount(courseOfferingRepository.save(offering));
    }

    @Transactional
    public void deactivateOffering(Long id) {
        CourseOffering offering = courseOfferingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CourseOffering", "id", id));
        offering.setIsActive(false);
        courseOfferingRepository.save(offering);
    }

    private CourseOfferingDto mapOfferingWithCount(CourseOffering co) {
        CourseOfferingDto dto = CourseOfferingDto.fromEntity(co);
        long count = enrollmentRepository.countByCourseOfferingIdAndStatus(co.getId(), EnrollmentStatus.ACTIVE);
        dto.setEnrolledStudentsCount(count);
        return dto;
    }
}
