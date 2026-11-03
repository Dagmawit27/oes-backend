package com.oes.onlineexamsystem.notification.controller;

import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.notification.dto.AnnouncementRequest;
import com.oes.onlineexamsystem.notification.dto.UnreadCountResponse;
import com.oes.onlineexamsystem.notification.service.NotificationService;
import com.oes.onlineexamsystem.schedule.dto.ExamNotificationDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<ExamNotificationDto>>> getMyNotifications(
            @RequestParam(required = false, defaultValue = "false") Boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<ExamNotificationDto> page = notificationService.getUserNotifications(principal.getId(), unreadOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved successfully", page));
    }

    @GetMapping("/recent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ExamNotificationDto>>> getRecentNotifications(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<ExamNotificationDto> list = notificationService.getRecentUserNotifications(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Recent notifications retrieved successfully", list));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        long count = notificationService.getUnreadCount(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved successfully", new UnreadCountResponse(count)));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ExamNotificationDto>> markNotificationAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ExamNotificationDto updated = notificationService.markAsRead(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", updated));
    }

    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal principal) {
        int updated = notificationService.markAllAsRead(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", Map.of("updatedCount", updated)));
    }

    @PostMapping("/announcements")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamNotificationDto>> broadcastAnnouncement(
            @Valid @RequestBody AnnouncementRequest request) {
        ExamNotificationDto announcement = notificationService.broadcastAnnouncement(request);
        return ResponseEntity.ok(ApiResponse.success("Announcement broadcast successfully", announcement));
    }
}
