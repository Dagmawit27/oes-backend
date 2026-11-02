package com.oes.onlineexamsystem.schedule.dto;

import com.oes.onlineexamsystem.schedule.entity.ExamRoom;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamRoomDto {

    private Long id;
    private String name;
    private String building;
    private Integer capacity;
    private Boolean isActive;
    private Instant createdAt;

    public static ExamRoomDto fromEntity(ExamRoom room) {
        if (room == null) return null;
        return ExamRoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .building(room.getBuilding())
                .capacity(room.getCapacity())
                .isActive(room.getIsActive())
                .createdAt(room.getCreatedAt())
                .build();
    }
}
