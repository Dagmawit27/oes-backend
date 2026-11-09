package com.oes.onlineexamsystem.execution.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeartbeatRequest {
    private Integer clientRemainingSeconds;
    private Boolean isTabActive;
}
