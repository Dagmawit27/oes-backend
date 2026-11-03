package com.oes.onlineexamsystem.notification.service;

import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.notification.dto.AnnouncementRequest;
import com.oes.onlineexamsystem.schedule.dto.ExamNotificationDto;
import com.oes.onlineexamsystem.schedule.entity.ExamEligibility;
import com.oes.onlineexamsystem.schedule.entity.ExamNotification;
import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import com.oes.onlineexamsystem.schedule.entity.NotificationType;
import com.oes.onlineexamsystem.schedule.repository.ExamEligibilityRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamNotificationRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamScheduleRepository;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ExamNotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ExamScheduleRepository scheduleRepository;
    private final ExamEligibilityRepository eligibilityRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(ExamNotificationRepository notificationRepository,
                               UserRepository userRepository,
                               ExamScheduleRepository scheduleRepository,
                               ExamEligibilityRepository eligibilityRepository,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public ExamNotificationDto sendNotificationToUser(User user,
                                                      NotificationType type,
                                                      String title,
                                                      String message,
                                                      ExamSchedule schedule) {
        ExamNotification notification = ExamNotification.builder()
                .user(user)
                .schedule(schedule)
                .eventType(type)
                .title(title)
                .message(message)
                .isRead(false)
                .build();

        ExamNotification saved = notificationRepository.save(notification);
        ExamNotificationDto dto = ExamNotificationDto.fromEntity(saved);

        // Real-time dispatch to user-specific queue via STOMP
        try {
            messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/notifications", dto);
            log.debug("Real-time STOMP notification sent to user {}: {}", user.getEmail(), title);
        } catch (Exception e) {
            log.warn("Failed to deliver STOMP notification to user {}: {}", user.getEmail(), e.getMessage());
        }

        return dto;
    }

    @Transactional
    public List<ExamNotificationDto> sendNotificationToUsers(List<User> users,
                                                             NotificationType type,
                                                             String title,
                                                             String message,
                                                             ExamSchedule schedule) {
        List<ExamNotificationDto> result = new ArrayList<>();
        for (User user : users) {
            result.add(sendNotificationToUser(user, type, title, message, schedule));
        }
        return result;
    }

    @Transactional
    public ExamNotificationDto broadcastAnnouncement(AnnouncementRequest request) {
        ExamSchedule schedule = null;
        if (request.getScheduleId() != null) {
            schedule = scheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Exam schedule not found with id: " + request.getScheduleId()));
        }

        List<User> recipients = new ArrayList<>();
        if (schedule != null) {
            // Find all eligible candidates enrolled in this schedule
            List<ExamEligibility> eligibilities = eligibilityRepository.findByScheduleId(schedule.getId());
            for (ExamEligibility el : eligibilities) {
                if (el.getStudent() != null && el.getStudent().getUser() != null) {
                    recipients.add(el.getStudent().getUser());
                }
            }
        } else {
            // All active students or users
            recipients = userRepository.findAll().stream()
                    .filter(User::getIsActive)
                    .toList();
        }

        // Save notification records and push to individual queues
        sendNotificationToUsers(recipients, NotificationType.ANNOUNCEMENT, request.getTitle(), request.getMessage(), schedule);

        // Also broadcast globally to /topic/notifications/announcements
        ExamNotificationDto announcementDto = ExamNotificationDto.builder()
                .title(request.getTitle())
                .message(request.getMessage())
                .eventType(NotificationType.ANNOUNCEMENT)
                .scheduleId(schedule != null ? schedule.getId() : null)
                .examinationTitle(schedule != null && schedule.getExamination() != null ? schedule.getExamination().getTitle() : null)
                .isRead(false)
                .build();

        try {
            messagingTemplate.convertAndSend("/topic/notifications/announcements", announcementDto);
            log.info("Broadcast announcement sent to /topic/notifications/announcements: {}", request.getTitle());
        } catch (Exception e) {
            log.warn("Failed to broadcast announcement to topic: {}", e.getMessage());
        }

        return announcementDto;
    }

    @Transactional(readOnly = true)
    public Page<ExamNotificationDto> getUserNotifications(Long userId, Boolean unreadOnly, Pageable pageable) {
        if (Boolean.TRUE.equals(unreadOnly)) {
            return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                    .map(ExamNotificationDto::fromEntity);
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(ExamNotificationDto::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<ExamNotificationDto> getRecentUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(20)
                .map(ExamNotificationDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public ExamNotificationDto markAsRead(Long notificationId, Long userId) {
        ExamNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (!notification.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You are not authorized to modify this notification");
        }

        notification.setIsRead(true);
        ExamNotification updated = notificationRepository.save(notification);
        return ExamNotificationDto.fromEntity(updated);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsReadForUser(userId);
    }
}
