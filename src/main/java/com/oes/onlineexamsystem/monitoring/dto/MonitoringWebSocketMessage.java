package com.oes.onlineexamsystem.monitoring.dto;

import lombok.*;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoringWebSocketMessage {
    private String messageType; // "EVENT", "METRIC_UPDATE", "ANNOUNCEMENT"
    private Long scheduleId;
    private MonitoringEventDto event;
    private CandidateMonitoringDto candidate;
    private Map<String, Object> metrics;
    private Instant timestamp;
}
