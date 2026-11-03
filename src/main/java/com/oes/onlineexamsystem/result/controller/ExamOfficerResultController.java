package com.oes.onlineexamsystem.result.controller;

import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.result.dto.CourseExamRosterItemDto;
import com.oes.onlineexamsystem.result.entity.ExamResult;
import com.oes.onlineexamsystem.result.service.GradingAndResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/exam-officer")
@RequiredArgsConstructor
public class ExamOfficerResultController {

    private final GradingAndResultService gradingAndResultService;

    /**
     * Retrieve complete candidate results roster for an examination.
     */
    @GetMapping("/exams/{examId}/results")
    @PreAuthorize("hasAnyRole('ROLE_EXAM_OFFICER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<CourseExamRosterItemDto>>> getExamResults(
            @PathVariable Long examId,
            @AuthenticationPrincipal UserPrincipal principal) {

        List<CourseExamRosterItemDto> roster = gradingAndResultService.getCourseExamRoster(examId, principal.getId(), true, true);
        return ResponseEntity.ok(ApiResponse.success("Course examination results roster retrieved", roster));
    }

    /**
     * Publish an individual completed result.
     */
    @PostMapping("/results/{resultId}/publish")
    @PreAuthorize("hasAnyRole('ROLE_EXAM_OFFICER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> publishResult(
            @PathVariable Long resultId,
            @AuthenticationPrincipal UserPrincipal principal) {

        gradingAndResultService.publishResult(resultId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Result published successfully", null));
    }

    /**
     * Publish all completed results for an examination sitting.
     */
    @PostMapping("/exams/{examId}/publish-all")
    @PreAuthorize("hasAnyRole('ROLE_EXAM_OFFICER', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> publishAllExamResults(
            @PathVariable Long examId,
            @AuthenticationPrincipal UserPrincipal principal) {

        int publishedCount = gradingAndResultService.publishAllExamResults(examId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Results published successfully", Map.of(
                "examinationId", examId,
                "publishedCount", publishedCount
        )));
    }
}
