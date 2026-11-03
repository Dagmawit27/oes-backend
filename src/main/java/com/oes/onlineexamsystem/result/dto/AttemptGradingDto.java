package com.oes.onlineexamsystem.result.dto;

import com.oes.onlineexamsystem.result.entity.GradingStatus;
import com.oes.onlineexamsystem.result.entity.PublicationStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttemptGradingDto {
    private Long attemptId;
    private Long resultId;
    private Long examinationId;
    private String examinationTitle;
    private Long studentId;
    private String studentRegistrationNumber;
    private String studentName;
    private BigDecimal totalMarks;
    private BigDecimal currentEarnedMarks;
    private GradingStatus gradingStatus;
    private PublicationStatus publicationStatus;
    private String gradedBy;
    private Instant gradedAt;

    @Builder.Default
    private List<QuestionGradingItemDto> questions = new ArrayList<>();
}
