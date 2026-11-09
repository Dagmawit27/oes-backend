package com.oes.onlineexamsystem.execution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeExamRequest {

    @NotNull(message = "Attempt ID is required")
    private Long attemptId;

    @Pattern(regexp = "^\\d{4}$", message = "Passcode must be exactly 4 numeric digits")
    private String passcode;
}
