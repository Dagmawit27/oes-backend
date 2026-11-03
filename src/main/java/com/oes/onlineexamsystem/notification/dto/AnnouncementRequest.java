package com.oes.onlineexamsystem.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    private Long scheduleId;

    private String targetRole; // e.g. "ROLE_STUDENT", "ROLE_INSTRUCTOR", or null/empty for ALL
}
