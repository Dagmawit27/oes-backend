package com.oes.onlineexamsystem.result.controller;

import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.result.dto.StudentCourseResultsDto;
import com.oes.onlineexamsystem.result.service.GradingAndResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/student/results")
@RequiredArgsConstructor
public class StudentResultController {

    private final GradingAndResultService gradingAndResultService;

    /**
     * Retrieve student's academic results grouped by course.
     * Shows earned marks / total marks only for PUBLISHED results.
     * Shows 'Not Attempted' or 'Pending' otherwise.
     * Never displays pass/fail status or labels.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_STUDENT', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<StudentCourseResultsDto>>> getMyResults(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<StudentCourseResultsDto> results = gradingAndResultService.getStudentCourseResults(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Course examination results retrieved successfully", results));
    }
}
