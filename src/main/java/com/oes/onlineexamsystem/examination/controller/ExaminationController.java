package com.oes.onlineexamsystem.examination.controller;

import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.common.dto.PagedResponse;
import com.oes.onlineexamsystem.examination.dto.*;
import com.oes.onlineexamsystem.examination.entity.ExamStatus;
import com.oes.onlineexamsystem.examination.service.ExaminationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/examinations")
public class ExaminationController {

    private final ExaminationService examinationService;

    public ExaminationController(ExaminationService examinationService) {
        this.examinationService = examinationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<ExaminationDto>>> getExaminations(
            @RequestParam(required = false) Long courseOfferingId,
            @RequestParam(required = false) ExamStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean myExamsOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "id,desc") String[] sort,
            @AuthenticationPrincipal UserPrincipal principal) {

        Sort.Direction direction = Sort.Direction.fromString(sort.length > 1 ? sort[1] : "desc");
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));

        boolean isStaff = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_EXAM_OFFICER"));

        Long createdById = null;
        if (!isStaff || Boolean.TRUE.equals(myExamsOnly)) {
            createdById = principal.getId();
        }

        PagedResponse<ExaminationDto> response = examinationService.getExaminations(
                courseOfferingId, status, createdById, search, pageable);

        return ResponseEntity.ok(ApiResponse.success("Examinations retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExaminationDto>> getExaminationById(@PathVariable Long id) {
        ExaminationDto dto = examinationService.getExaminationById(id);
        return ResponseEntity.ok(ApiResponse.success("Examination retrieved successfully", dto));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExaminationDto>> createExamination(
            @Valid @RequestBody CreateExaminationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ExaminationDto created = examinationService.createExamination(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Examination draft created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExaminationDto>> updateExamination(
            @PathVariable Long id,
            @Valid @RequestBody UpdateExaminationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        ExaminationDto updated = examinationService.updateExamination(id, request, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Examination updated successfully", updated));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExaminationDto>> submitForApproval(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        ExaminationDto submitted = examinationService.submitForApproval(id, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Examination submitted for review successfully", submitted));
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExaminationDto>> reviewExamination(
            @PathVariable Long id,
            @Valid @RequestBody ReviewExaminationRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ExaminationDto reviewed = examinationService.reviewExamination(id, request, principal.getId());
        String msg = request.getDecision().name().equals("APPROVED") ?
                "Examination approved successfully and question snapshots locked." :
                "Examination rejected with feedback provided to instructor.";
        return ResponseEntity.ok(ApiResponse.success(msg, reviewed));
    }

    @GetMapping("/{id}/approvals")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ExamApprovalDto>>> getExaminationApprovals(@PathVariable Long id) {
        List<ExamApprovalDto> approvals = examinationService.getExaminationApprovals(id);
        return ResponseEntity.ok(ApiResponse.success("Approval history retrieved successfully", approvals));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteExamination(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        examinationService.deleteExamination(id, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Examination deleted successfully", null));
    }
}
