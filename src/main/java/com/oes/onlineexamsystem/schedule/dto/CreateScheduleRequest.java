package com.oes.onlineexamsystem.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateScheduleRequest {

    @NotNull(message = "Examination ID is required")
    private Long examinationId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate examinationDate;

    @JsonFormat(pattern = "HH:mm[:ss]")
    private LocalTime scheduledStartTime;

    @Builder.Default
    private String timezone = "Africa/Addis_Ababa";

    @Pattern(regexp = "^\\d{4}$", message = "Passcode 1 must be exactly 4 numeric digits")
    private String passcode1;

    @Pattern(regexp = "^\\d{4}$", message = "Passcode 2 must be exactly 4 numeric digits")
    private String passcode2;

    @Pattern(regexp = "^\\d{4}$", message = "Passcode 3 must be exactly 4 numeric digits")
    private String passcode3;

    private Long roomId;

    private String instructions;

    @Builder.Default
    private Boolean autoGenerateEligibility = true;

    // Backward-compatible fallback fields (optional)
    private Instant scheduledStartAt;
    private Instant scheduledCloseAt;
    private Integer durationMinutes;
}
