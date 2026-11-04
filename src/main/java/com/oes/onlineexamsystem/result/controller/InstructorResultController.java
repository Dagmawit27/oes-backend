package com.oes.onlineexamsystem.result.controller;

import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.result.dto.*;
import com.oes.onlineexamsystem.result.entity.ExamResult;
import com.oes.onlineexamsystem.result.service.GradingAndResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instructor")
@RequiredArgsConstructor
public class InstructorResultController {

    private final GradingAndResultService gradingAndResultService;

    /**
     * Retrieve live metrics, counts, and recent examinations for instructor dashboard.
     */
    @GetMapping("/dashboard-stats")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'EXAM_OFFICER', 'ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_EXAM_OFFICER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<InstructorDashboardStatsDto>> getDashboardStats(
            @AuthenticationPrincipal UserPrincipal principal) {
        InstructorDashboardStatsDto stats = gradingAndResultService.getInstructorDashboardStats(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Instructor dashboard metrics retrieved", stats));
    }

    /**
     * Retrieve examination roster results for an assigned course examination.
     */
    @GetMapping("/exams/{examId}/results")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'EXAM_OFFICER', 'ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_EXAM_OFFICER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<CourseExamRosterItemDto>>> getExamResults(
            @PathVariable Long examId,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EXAM_OFFICER"));

        List<CourseExamRosterItemDto> roster = gradingAndResultService.getCourseExamRoster(examId, principal.getId(), isAdmin, isOfficer);
        return ResponseEntity.ok(ApiResponse.success("Examination student results roster retrieved", roster));
    }

    /**
     * Retrieve full grading view for an attempt (questions, student answers, maximum marks, current marks).
     */
    @GetMapping("/attempts/{attemptId}/grading")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'EXAM_OFFICER', 'ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_EXAM_OFFICER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AttemptGradingDto>> getAttemptGrading(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EXAM_OFFICER"));

        AttemptGradingDto dto = gradingAndResultService.getAttemptGrading(attemptId, principal.getId(), isAdmin, isOfficer);
        return ResponseEntity.ok(ApiResponse.success("Attempt grading details retrieved", dto));
    }

    /**
     * Save draft manual grades for an attempt without finalizing.
     */
    @PutMapping("/attempts/{attemptId}/grades")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AttemptGradingDto>> saveDraftGrades(
            @PathVariable Long attemptId,
            @Valid @RequestBody SaveGradesRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EXAM_OFFICER"));

        AttemptGradingDto dto = gradingAndResultService.saveDraftGrades(attemptId, request, principal.getId(), isAdmin, isOfficer);
        return ResponseEntity.ok(ApiResponse.success("Draft grades saved successfully", dto));
    }

    /**
     * Finalize and complete grading for an attempt. Validates all questions are awarded marks.
     */
    @PostMapping("/attempts/{attemptId}/complete-grading")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AttemptGradingDto>> completeGrading(
            @PathVariable Long attemptId,
            @Valid @RequestBody(required = false) SaveGradesRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EXAM_OFFICER"));

        AttemptGradingDto dto = gradingAndResultService.completeGrading(attemptId, request, principal.getId(), isAdmin, isOfficer);
        return ResponseEntity.ok(ApiResponse.success("Grading completed successfully", dto));
    }

    /**
     * Audited correction to a published result.
     */
    @PostMapping("/results/{resultId}/correct")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'EXAM_OFFICER', 'ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_EXAM_OFFICER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> correctPublishedResult(
            @PathVariable Long resultId,
            @Valid @RequestBody ResultCorrectionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EXAM_OFFICER"));

        gradingAndResultService.correctPublishedResult(resultId, request, principal.getId(), isAdmin, isOfficer);
        return ResponseEntity.ok(ApiResponse.success("Published result corrected and audited successfully", null));
    }
}
