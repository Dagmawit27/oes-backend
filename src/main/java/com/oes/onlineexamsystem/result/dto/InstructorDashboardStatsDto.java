package com.oes.onlineexamsystem.result.dto;

import com.oes.onlineexamsystem.examination.dto.ExaminationDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstructorDashboardStatsDto {
    private long assignedCoursesCount;
    private long totalEnrolledStudents;
    private long totalQuestionsCount;
    private long totalExamsCount;
    private long draftExamsCount;
    private long pendingReviewExamsCount;
    private long approvedExamsCount;
    private long pendingGradingSubmissionsCount;
    private long completedGradingCount;
    private List<ExaminationDto> recentExams;
}
