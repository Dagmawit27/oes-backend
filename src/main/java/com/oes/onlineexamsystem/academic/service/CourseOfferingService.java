package com.oes.onlineexamsystem.academic.service;

import com.oes.onlineexamsystem.academic.dto.CourseOfferingDto;
import com.oes.onlineexamsystem.academic.entity.*;
import com.oes.onlineexamsystem.academic.repository.*;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.common.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class CourseOfferingService {

    private final CourseOfferingRepository courseOfferingRepository;
    private final CourseRepository courseRepository;
    private final SemesterRepository semesterRepository;
    private final InstructorRepository instructorRepository;
    private final ProgramRepository programRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final InstructorService instructorService;

    public CourseOfferingService(CourseOfferingRepository courseOfferingRepository,
                                 CourseRepository courseRepository,
                                 SemesterRepository semesterRepository,
                                 InstructorRepository instructorRepository,
                                 ProgramRepository programRepository,
                                 EnrollmentRepository enrollmentRepository,
                                 InstructorService instructorService) {
        this.courseOfferingRepository = courseOfferingRepository;
        this.courseRepository = courseRepository;
        this.semesterRepository = semesterRepository;
        this.instructorRepository = instructorRepository;
        this.programRepository = programRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.instructorService = instructorService;
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
                .orElseThrow(() -> new ResourceNotFoundException("Course Assignment", "id", id));
        return mapOfferingWithCount(offering);
    }

    @Transactional(readOnly = true)
    public List<CourseOfferingDto> getOfferingsBySemester(Long semesterId) {
        return courseOfferingRepository.findBySemesterId(semesterId).stream()
                .map(this::mapOfferingWithCount)
                .toList();
    }

    @Transactional
    public List<CourseOfferingDto> getOfferingsByInstructorUserId(Long userId) {
        Instructor instructor = instructorRepository.findByUserId(userId).orElse(null);
        if (instructor == null) {
            instructorService.syncInstructorsFromUsers();
            instructor = instructorRepository.findByUserId(userId).orElse(null);
        }
        if (instructor == null) {
            return Collections.emptyList();
        }

        return courseOfferingRepository.findByInstructorId(instructor.getId()).stream()
                .map(this::mapOfferingWithCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public void verifyInstructorAccess(Long offeringId, Long userId, boolean isPrivileged) {
        if (isPrivileged) return;
        CourseOffering offering = courseOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new ResourceNotFoundException("Course Assignment", "id", offeringId));
        if (offering.getInstructor() == null || !offering.getInstructor().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to view students for this course assignment");
        }
    }

    private Program resolveProgram(Long programId) {
        if (programId == null) {
            return null;
        }
        return programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", programId));
    }

    @Transactional
    public CourseOfferingDto createOffering(CourseOfferingDto dto) {
        String normalizedSection = dto.getSection().trim().toUpperCase();
        if (courseOfferingRepository.existsByCourseIdAndSemesterIdAndSection(
                dto.getCourseId(), dto.getSemesterId(), normalizedSection)) {
            throw new DuplicateResourceException("Course Assignment", "course, semester, and section",
                    dto.getCourseId() + " in section " + dto.getSection());
        }

        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", dto.getCourseId()));

        Semester semester = semesterRepository.findById(dto.getSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", dto.getSemesterId()));

        Instructor instructor = instructorRepository.findById(dto.getInstructorId())
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", "id", dto.getInstructorId()));

        Program program = resolveProgram(dto.getProgramId());

        CourseOffering offering = CourseOffering.builder()
                .course(course)
                .semester(semester)
                .instructor(instructor)
                .program(program)
                .studentYearLevel(dto.getStudentYearLevel())
                .section(normalizedSection)
                .maxStudents(dto.getMaxStudents() != null ? dto.getMaxStudents() : 50)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return mapOfferingWithCount(courseOfferingRepository.save(offering));
    }

    @Transactional
    public CourseOfferingDto updateOffering(Long id, CourseOfferingDto dto) {
        CourseOffering offering = courseOfferingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course Assignment", "id", id));

        String normalizedSection = dto.getSection().trim().toUpperCase();
        courseOfferingRepository.findByCourseIdAndSemesterIdAndSection(
                        dto.getCourseId(), dto.getSemesterId(), normalizedSection)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Course Assignment", "course, semester, and section",
                            dto.getCourseId() + " in section " + dto.getSection());
                });

        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", dto.getCourseId()));

        Semester semester = semesterRepository.findById(dto.getSemesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", dto.getSemesterId()));

        Instructor instructor = instructorRepository.findById(dto.getInstructorId())
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", "id", dto.getInstructorId()));

        Program program = resolveProgram(dto.getProgramId());

        offering.setCourse(course);
        offering.setSemester(semester);
        offering.setInstructor(instructor);
        offering.setProgram(program);
        offering.setStudentYearLevel(dto.getStudentYearLevel());
        offering.setSection(normalizedSection);
        if (dto.getMaxStudents() != null) {
            offering.setMaxStudents(dto.getMaxStudents());
        }
        if (dto.getIsActive() != null) {
            offering.setIsActive(dto.getIsActive());
        }

        return mapOfferingWithCount(courseOfferingRepository.save(offering));
    }

    @Transactional
    public CourseOfferingDto toggleOfferingStatus(Long id, Boolean isActive) {
        CourseOffering offering = courseOfferingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course Assignment", "id", id));
        boolean targetStatus = isActive != null ? isActive : !Boolean.TRUE.equals(offering.getIsActive());
        offering.setIsActive(targetStatus);
        return mapOfferingWithCount(courseOfferingRepository.save(offering));
    }

    @Transactional
    public void deactivateOffering(Long id) {
        CourseOffering offering = courseOfferingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course Assignment", "id", id));
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
