package com.oes.onlineexamsystem.question.controller;

import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.common.dto.PagedResponse;
import com.oes.onlineexamsystem.question.dto.*;
import com.oes.onlineexamsystem.question.entity.QuestionType;
import com.oes.onlineexamsystem.question.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'EXAM_OFFICER')")
    public ResponseEntity<ApiResponse<PagedResponse<QuestionDto>>> getQuestions(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long bankId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean myQuestionsOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort,
            @AuthenticationPrincipal UserPrincipal principal) {

        Sort.Direction direction = Sort.Direction.fromString(sort.length > 1 ? sort[1] : "desc");
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));

        Long createdById = null;
        if (Boolean.TRUE.equals(myQuestionsOnly) && principal != null) {
            createdById = principal.getId();
        }

        PagedResponse<QuestionDto> response = questionService.getQuestions(
                courseId, bankId, categoryId, type, search, isActive, createdById, pageable);

        return ResponseEntity.ok(ApiResponse.success("Questions retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'EXAM_OFFICER')")
    public ResponseEntity<ApiResponse<QuestionDto>> getQuestionById(@PathVariable Long id) {
        QuestionDto question = questionService.getQuestionById(id);
        return ResponseEntity.ok(ApiResponse.success("Question retrieved successfully", question));
    }

    /**
     * CRITICAL SECURITY ENDPOINT:
     * Returns sanitized question details without correct answer markers or internal explanations.
     */
    @GetMapping("/{id}/student-view")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'ADMIN', 'EXAM_OFFICER')")
    public ResponseEntity<ApiResponse<StudentQuestionDto>> getStudentQuestionById(@PathVariable Long id) {
        StudentQuestionDto studentQuestion = questionService.getStudentQuestionById(id);
        return ResponseEntity.ok(ApiResponse.success("Question view retrieved successfully", studentQuestion));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<QuestionDto>> createQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuestionDto created = questionService.createQuestion(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Question created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<QuestionDto>> updateQuestion(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuestionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        QuestionDto updated = questionService.updateQuestion(id, request, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Question updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        questionService.deleteQuestion(id, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Question archived successfully", null));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<QuestionDto>> duplicateQuestion(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuestionDto duplicated = questionService.duplicateQuestion(id, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Question duplicated successfully for exam reuse", duplicated));
    }
}
