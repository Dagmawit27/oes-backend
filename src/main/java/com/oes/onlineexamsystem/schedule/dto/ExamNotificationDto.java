package com.oes.onlineexamsystem.schedule.dto;

import com.oes.onlineexamsystem.schedule.entity.ExamNotification;
import com.oes.onlineexamsystem.schedule.entity.NotificationType;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamNotificationDto {

    private Long id;
    private Long scheduleId;
    private Long userId;
    private NotificationType eventType;
    private String title;
    private String message;
    private Boolean isRead;
    private Instant createdAt;

    public static ExamNotificationDto fromEntity(ExamNotification n) {
        if (n == null) return null;
        return ExamNotificationDto.builder()
                .id(n.getId())
                .scheduleId(n.getSchedule() != null ? n.getSchedule().getId() : null)
                .userId(n.getUser() != null ? n.getUser().getId() : null)
                .eventType(n.getEventType())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
