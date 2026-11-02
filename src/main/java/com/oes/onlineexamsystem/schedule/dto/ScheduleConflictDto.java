package com.oes.onlineexamsystem.schedule.dto;

import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleConflictDto {

    private boolean hasConflict;
    private String roomConflictMessage;

    @Builder.Default
    private List<StudentConflictItem> studentConflicts = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentConflictItem {
        private Long studentId;
        private String studentIdNumber;
        private String studentName;
        private Long conflictingScheduleId;
        private String conflictingExamTitle;
        private Instant conflictingStart;
        private Instant conflictingClose;
    }
}
