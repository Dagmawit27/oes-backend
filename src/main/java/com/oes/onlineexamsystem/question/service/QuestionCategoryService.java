package com.oes.onlineexamsystem.question.service;

import com.oes.onlineexamsystem.academic.entity.Course;
import com.oes.onlineexamsystem.academic.repository.CourseRepository;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.question.dto.QuestionCategoryDto;
import com.oes.onlineexamsystem.question.entity.QuestionCategory;
import com.oes.onlineexamsystem.question.repository.QuestionCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionCategoryService {

    private final QuestionCategoryRepository categoryRepository;
    private final CourseRepository courseRepository;

    public QuestionCategoryService(QuestionCategoryRepository categoryRepository,
                                   CourseRepository courseRepository) {
        this.categoryRepository = categoryRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public List<QuestionCategoryDto> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(QuestionCategoryDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionCategoryDto> getCategoriesByCourse(Long courseId) {
        return categoryRepository.findByCourseId(courseId).stream()
                .map(QuestionCategoryDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionCategoryDto getCategoryById(Long id) {
        QuestionCategory cat = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionCategory", "id", id));
        return QuestionCategoryDto.fromEntity(cat);
    }

    @Transactional
    public QuestionCategoryDto createCategory(QuestionCategoryDto dto) {
        if (categoryRepository.existsByCodeIgnoreCase(dto.getCode().trim())) {
            throw new DuplicateResourceException("QuestionCategory", "code", dto.getCode());
        }

        Course course = null;
        if (dto.getCourseId() != null) {
            course = courseRepository.findById(dto.getCourseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Course", "id", dto.getCourseId()));
        }

        QuestionCategory category = QuestionCategory.builder()
                .course(course)
                .name(dto.getName().trim())
                .code(dto.getCode().trim().toUpperCase())
                .description(dto.getDescription())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return QuestionCategoryDto.fromEntity(categoryRepository.save(category));
    }

    @Transactional
    public void deactivateCategory(Long id) {
        QuestionCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionCategory", "id", id));
        category.setIsActive(false);
        categoryRepository.save(category);
    }
}
