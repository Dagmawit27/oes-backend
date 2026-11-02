package com.oes.onlineexamsystem.question.controller;

import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.question.dto.QuestionCategoryDto;
import com.oes.onlineexamsystem.question.service.QuestionCategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/question-categories")
public class QuestionCategoryController {

    private final QuestionCategoryService categoryService;

    public QuestionCategoryController(QuestionCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionCategoryDto>>> getAllCategories(
            @RequestParam(required = false) Long courseId) {
        List<QuestionCategoryDto> categories = courseId != null
                ? categoryService.getCategoriesByCourse(courseId)
                : categoryService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success("Question categories retrieved successfully", categories));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionCategoryDto>> getCategoryById(@PathVariable Long id) {
        QuestionCategoryDto category = categoryService.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success("Category retrieved successfully", category));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<QuestionCategoryDto>> createCategory(
            @Valid @RequestBody QuestionCategoryDto dto) {
        QuestionCategoryDto created = categoryService.createCategory(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", created));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateCategory(@PathVariable Long id) {
        categoryService.deactivateCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deactivated successfully", null));
    }
}
