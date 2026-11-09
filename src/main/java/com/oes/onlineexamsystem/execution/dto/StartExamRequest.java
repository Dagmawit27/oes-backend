package com.oes.onlineexamsystem.execution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartExamRequest {

    @NotNull(message = "Schedule ID is required")
    private Long scheduleId;

    @Pattern(regexp = "^\\d{4}$", message = "Passcode must be exactly 4 numeric digits")
    private String passcode;
}
