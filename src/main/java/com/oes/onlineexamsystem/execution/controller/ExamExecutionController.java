package com.oes.onlineexamsystem.execution.controller;

import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.execution.dto.*;
import com.oes.onlineexamsystem.execution.service.ExamExecutionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/exam-execution")
public class ExamExecutionController {

    private final ExamExecutionService executionService;

    public ExamExecutionController(ExamExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamExecutionPayloadDto>> startOrResumeExam(
            @RequestParam Long scheduleId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {

        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        ExamExecutionPayloadDto payload = executionService.startOrResumeExam(
                scheduleId, principal.getId(), ipAddress, userAgent
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Exam attempt initialized successfully", payload));
    }

    @PostMapping("/attempts/{attemptId}/answers")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<StudentAnswerDto>> saveAnswer(
            @PathVariable Long attemptId,
            @Valid @RequestBody SaveAnswerRequest saveRequest,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {

        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        StudentAnswerDto savedAnswer = executionService.saveAnswer(
                attemptId, saveRequest, principal.getId(), ipAddress, userAgent
        );

        return ResponseEntity.ok(ApiResponse.success("Answer saved successfully", savedAnswer));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamSubmissionResultDto>> submitExam(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {

        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        ExamSubmissionResultDto result = executionService.submitExam(
                attemptId, principal.getId(), ipAddress, userAgent
        );

        return ResponseEntity.ok(ApiResponse.success("Examination submitted and finalized successfully", result));
    }

    @PostMapping("/attempts/{attemptId}/events")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> logClientEvent(
            @PathVariable Long attemptId,
            @Valid @RequestBody ExamEventLogRequest eventRequest,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) {

        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        executionService.logClientEvent(attemptId, eventRequest, principal.getId(), ipAddress, userAgent);

        return ResponseEntity.ok(ApiResponse.success("Client telemetry event recorded", null));
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
