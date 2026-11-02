package com.oes.onlineexamsystem.schedule.controller;

import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.common.dto.PagedResponse;
import com.oes.onlineexamsystem.schedule.dto.*;
import com.oes.onlineexamsystem.schedule.entity.ScheduleStatus;
import com.oes.onlineexamsystem.schedule.service.ExamScheduleService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
public class ExamScheduleController {

    private final ExamScheduleService scheduleService;

    public ExamScheduleController(ExamScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<PagedResponse<ExamScheduleDto>>> getSchedules(
            @RequestParam(required = false) Long courseOfferingId,
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "scheduledStartAt,asc") String[] sort) {

        Sort.Direction direction = Sort.Direction.fromString(sort.length > 1 ? sort[1] : "asc");
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sort[0]));

        PagedResponse<ExamScheduleDto> response = scheduleService.getSchedules(
                courseOfferingId, status, from, to, pageable);

        return ResponseEntity.ok(ApiResponse.success("Exam schedules retrieved successfully", response));
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<List<ExamScheduleDto>>> getCalendarSchedules(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        List<ExamScheduleDto> schedules = scheduleService.getCalendarSchedules(start, end);
        return ResponseEntity.ok(ApiResponse.success("Calendar schedules retrieved successfully", schedules));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN', 'INSTRUCTOR', 'STUDENT')")
    public ResponseEntity<ApiResponse<ExamScheduleDto>> getScheduleById(@PathVariable Long id) {
        ExamScheduleDto schedule = scheduleService.getScheduleById(id);
        return ResponseEntity.ok(ApiResponse.success("Exam schedule retrieved successfully", schedule));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamScheduleDto>> createSchedule(
            @Valid @RequestBody CreateScheduleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ExamScheduleDto created = scheduleService.createSchedule(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Examination scheduled successfully", created));
    }

    @GetMapping("/check-conflicts")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ScheduleConflictDto>> checkConflicts(
            @RequestParam(required = false) Long examinationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant close,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long excludeScheduleId) {
        ScheduleConflictDto report = scheduleService.checkConflicts(examinationId, start, close, roomId, excludeScheduleId);
        return ResponseEntity.ok(ApiResponse.success("Conflict analysis completed", report));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamScheduleDto>> reschedule(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ExamScheduleDto rescheduled = scheduleService.reschedule(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Examination rescheduled successfully and students notified", rescheduled));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamScheduleDto>> cancelSchedule(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserPrincipal principal) {
        ExamScheduleDto cancelled = scheduleService.cancelSchedule(id, reason, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Examination schedule cancelled and students notified", cancelled));
    }

    @PostMapping("/{id}/eligibility")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamEligibilityDto>> addStudentEligibility(
            @PathVariable Long id,
            @Valid @RequestBody AddStudentEligibilityRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ExamEligibilityDto dto = scheduleService.addStudentEligibility(id, request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student eligibility added successfully", dto));
    }

    @PutMapping("/{id}/eligibility/{studentId}")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamEligibilityDto>> updateEligibility(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @Valid @RequestBody UpdateEligibilityRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ExamEligibilityDto updated = scheduleService.updateEligibility(id, studentId, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Student eligibility updated successfully", updated));
    }

    @DeleteMapping("/{id}/eligibility/{studentId}")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeStudentEligibility(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        scheduleService.removeStudentEligibility(id, studentId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Student eligibility removed successfully", null));
    }

    /**
     * STUDENT PORTAL ENDPOINTS
     */
    @GetMapping("/my-schedules")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<StudentScheduleDto>>> getStudentSchedules(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<StudentScheduleDto> schedules = scheduleService.getStudentSchedules(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Student schedules retrieved successfully", schedules));
    }

    @GetMapping("/my-notifications")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ExamNotificationDto>>> getMyNotifications(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ExamNotificationDto> notifications = scheduleService.getNotificationsForUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved successfully", notifications));
    }
}
