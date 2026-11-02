package com.oes.onlineexamsystem.academic.service;

import com.oes.onlineexamsystem.academic.dto.CourseDto;
import com.oes.onlineexamsystem.academic.entity.Course;
import com.oes.onlineexamsystem.academic.entity.Department;
import com.oes.onlineexamsystem.academic.repository.CourseRepository;
import com.oes.onlineexamsystem.academic.repository.DepartmentRepository;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final DepartmentRepository departmentRepository;

    public CourseService(CourseRepository courseRepository, DepartmentRepository departmentRepository) {
        this.courseRepository = courseRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseDto> getAllCourses() {
        return courseRepository.findAll().stream()
                .map(CourseDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDto getCourseById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        return CourseDto.fromEntity(course);
    }

    @Transactional
    public CourseDto createCourse(CourseDto dto) {
        if (courseRepository.existsByCodeIgnoreCase(dto.getCode().trim())) {
            throw new DuplicateResourceException("Course", "code", dto.getCode());
        }

        Department department = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", dto.getDepartmentId()));

        Course course = Course.builder()
                .department(department)
                .name(dto.getName().trim())
                .code(dto.getCode().trim().toUpperCase())
                .description(dto.getDescription())
                .credits(dto.getCredits() != null ? dto.getCredits() : 3)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return CourseDto.fromEntity(courseRepository.save(course));
    }

    @Transactional
    public CourseDto updateCourse(Long id, CourseDto dto) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));

        course.setName(dto.getName().trim());
        course.setDescription(dto.getDescription());
        if (dto.getCredits() != null) {
            course.setCredits(dto.getCredits());
        }
        if (dto.getIsActive() != null) {
            course.setIsActive(dto.getIsActive());
        }

        return CourseDto.fromEntity(courseRepository.save(course));
    }

    @Transactional
    public void deactivateCourse(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", id));
        course.setIsActive(false);
        courseRepository.save(course);
    }
}
