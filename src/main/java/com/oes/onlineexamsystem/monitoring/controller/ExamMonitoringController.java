package com.oes.onlineexamsystem.monitoring.controller;

import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.monitoring.dto.ExamMonitoringSnapshotDto;
import com.oes.onlineexamsystem.monitoring.dto.ScheduleMonitoringSummaryDto;
import com.oes.onlineexamsystem.monitoring.service.ExamMonitoringService;
import com.oes.onlineexamsystem.notification.dto.AnnouncementRequest;
import com.oes.onlineexamsystem.notification.service.NotificationService;
import com.oes.onlineexamsystem.schedule.dto.ExamNotificationDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/monitoring")
public class ExamMonitoringController {

    private final ExamMonitoringService monitoringService;
    private final NotificationService notificationService;

    public ExamMonitoringController(ExamMonitoringService monitoringService,
                                    NotificationService notificationService) {
        this.monitoringService = monitoringService;
        this.notificationService = notificationService;
    }

    @GetMapping("/schedules")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<ScheduleMonitoringSummaryDto>>> getActiveSchedules() {
        List<ScheduleMonitoringSummaryDto> list = monitoringService.getActiveSchedules();
        return ResponseEntity.ok(ApiResponse.success("Monitoring schedules retrieved successfully", list));
    }

    @GetMapping("/schedules/{scheduleId}/snapshot")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ExamMonitoringSnapshotDto>> getScheduleSnapshot(
            @PathVariable Long scheduleId) {
        ExamMonitoringSnapshotDto snapshot = monitoringService.getScheduleSnapshot(scheduleId);
        return ResponseEntity.ok(ApiResponse.success("Live examination snapshot retrieved successfully", snapshot));
    }

    @PostMapping("/schedules/{scheduleId}/announcement")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamNotificationDto>> sendLiveExamAnnouncement(
            @PathVariable Long scheduleId,
            @Valid @RequestBody AnnouncementRequest request) {
        monitoringService.verifyLiveAnnouncementAllowed(scheduleId);
        request.setScheduleId(scheduleId);
        ExamNotificationDto announcement = notificationService.broadcastAnnouncement(request);
        return ResponseEntity.ok(ApiResponse.success("Live exam announcement sent successfully", announcement));
    }
}
