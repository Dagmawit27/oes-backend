package com.oes.onlineexamsystem.monitoring.service;

import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.examination.repository.ExamQuestionRepository;
import com.oes.onlineexamsystem.execution.entity.AttemptStatus;
import com.oes.onlineexamsystem.execution.entity.ExamAttempt;
import com.oes.onlineexamsystem.execution.entity.ExamEvent;
import com.oes.onlineexamsystem.execution.entity.ExamEventType;
import com.oes.onlineexamsystem.execution.repository.ExamAttemptRepository;
import com.oes.onlineexamsystem.execution.repository.ExamEventRepository;
import com.oes.onlineexamsystem.execution.repository.StudentAnswerRepository;
import com.oes.onlineexamsystem.monitoring.dto.*;
import com.oes.onlineexamsystem.schedule.entity.EligibilityStatus;
import com.oes.onlineexamsystem.schedule.entity.ExamEligibility;
import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import com.oes.onlineexamsystem.schedule.entity.ScheduleStatus;
import com.oes.onlineexamsystem.schedule.repository.ExamEligibilityRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(ExamMonitoringService.class);
    private static final Duration INACTIVE_THRESHOLD = Duration.ofMinutes(3);

    private final ExamScheduleRepository scheduleRepository;
    private final ExamEligibilityRepository eligibilityRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamEventRepository eventRepository;
    private final StudentAnswerRepository answerRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ExamMonitoringService(ExamScheduleRepository scheduleRepository,
                                 ExamEligibilityRepository eligibilityRepository,
                                 ExamAttemptRepository attemptRepository,
                                 ExamEventRepository eventRepository,
                                 StudentAnswerRepository answerRepository,
                                 ExamQuestionRepository examQuestionRepository,
                                 SimpMessagingTemplate messagingTemplate) {
        this.scheduleRepository = scheduleRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.answerRepository = answerRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public ExamMonitoringSnapshotDto getScheduleSnapshot(Long scheduleId) {
        ExamSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam schedule not found with id: " + scheduleId));

        var exam = schedule.getExamination();
        int totalQuestions = (exam != null) ? examQuestionRepository.countByExaminationId(exam.getId()) : 0;

        List<ExamEligibility> eligibilities = eligibilityRepository.findByScheduleIdOrderByStudentRegistrationNumberAsc(scheduleId);
        List<ExamAttempt> attempts = attemptRepository.findByScheduleId(scheduleId);

        Map<Long, ExamAttempt> studentAttemptMap = attempts.stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), a -> a, (first, second) -> first));

        List<ExamEvent> recentEventsList = eventRepository.findTop50ByAttempt_ScheduleIdOrderByOccurredAtDesc(scheduleId);

        // Map attemptId to latest event
        Map<Long, ExamEvent> latestEventByAttempt = new HashMap<>();
        for (ExamEvent ev : recentEventsList) {
            latestEventByAttempt.putIfAbsent(ev.getAttempt().getId(), ev);
        }

        Instant now = Instant.now();
        List<CandidateMonitoringDto> candidates = new ArrayList<>();

        int startedCount = 0;
        int activeAttemptsCount = 0;
        int submittedCount = 0;
        int disconnectedCount = 0;

        for (ExamEligibility el : eligibilities) {
            var student = el.getStudent();
            if (student == null) continue;

            ExamAttempt attempt = studentAttemptMap.get(student.getId());
            CandidateMonitoringDto candidateDto;

            if (attempt != null) {
                startedCount++;
                boolean isAttemptActive = attempt.getStatus() == AttemptStatus.IN_PROGRESS;
                if (isAttemptActive) {
                    activeAttemptsCount++;
                } else if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
                    submittedCount++;
                }

                ExamEvent latestEvent = latestEventByAttempt.get(attempt.getId());
                boolean isOnline = isAttemptActive
                        && attempt.getLastActivityAt() != null
                        && Duration.between(attempt.getLastActivityAt(), now).compareTo(INACTIVE_THRESHOLD) <= 0
                        && (latestEvent == null || latestEvent.getEventType() != ExamEventType.DISCONNECTED);

                if (isAttemptActive && !isOnline) {
                    disconnectedCount++;
                }

                int answeredCount = answerRepository.countByAttemptId(attempt.getId());

                candidateDto = CandidateMonitoringDto.builder()
                        .studentId(student.getId())
                        .attemptId(attempt.getId())
                        .studentName(student.getUser().getFirstName() + " " + student.getUser().getLastName())
                        .registrationNumber(student.getRegistrationNumber())
                        .email(student.getUser().getEmail())
                        .status(attempt.getStatus().name())
                        .startedAt(attempt.getStartedAt())
                        .deadlineAt(attempt.getDeadlineAt())
                        .lastActivityAt(attempt.getLastActivityAt())
                        .isOnline(isOnline)
                        .answeredQuestions(answeredCount)
                        .totalQuestions(totalQuestions)
                        .lastEventType(latestEvent != null ? latestEvent.getEventType().name() : "STARTED")
                        .build();
            } else {
                candidateDto = CandidateMonitoringDto.builder()
                        .studentId(student.getId())
                        .studentName(student.getUser().getFirstName() + " " + student.getUser().getLastName())
                        .registrationNumber(student.getRegistrationNumber())
                        .email(student.getUser().getEmail())
                        .status("NOT_STARTED")
                        .isOnline(false)
                        .answeredQuestions(0)
                        .totalQuestions(totalQuestions)
                        .lastEventType("NOT_STARTED")
                        .build();
            }

            candidates.add(candidateDto);
        }

        int totalEligible = eligibilities.size();
        int notStartedCount = Math.max(0, totalEligible - startedCount);

        List<MonitoringEventDto> recentEventDtos = recentEventsList.stream()
                .map(ev -> MonitoringEventDto.builder()
                        .id(ev.getId())
                        .attemptId(ev.getAttempt().getId())
                        .studentId(ev.getAttempt().getStudent().getId())
                        .studentName(ev.getAttempt().getStudent().getUser().getFirstName() + " " +
                                     ev.getAttempt().getStudent().getUser().getLastName())
                        .registrationNumber(ev.getAttempt().getStudent().getRegistrationNumber())
                        .eventType(ev.getEventType())
                        .eventPayload(ev.getEventPayload())
                        .occurredAt(ev.getOccurredAt())
                        .build())
                .toList();

        return ExamMonitoringSnapshotDto.builder()
                .scheduleId(schedule.getId())
                .examinationId(exam != null ? exam.getId() : null)
                .examinationTitle(exam != null ? exam.getTitle() : "Examination")
                .courseCode(exam != null && exam.getCourseOffering() != null ? exam.getCourseOffering().getCourse().getCode() : "")
                .courseName(exam != null && exam.getCourseOffering() != null ? exam.getCourseOffering().getCourse().getName() : "")
                .scheduleStatus(schedule.getStatus().name())
                .scheduledStartAt(schedule.getScheduledStartAt())
                .scheduledCloseAt(schedule.getScheduledCloseAt())
                .durationMinutes(schedule.getDurationMinutes())
                .roomName(schedule.getRoom() != null ? schedule.getRoom().getName() : "Online / Remote")
                .totalEligibleStudents(totalEligible)
                .startedCount(startedCount)
                .notStartedCount(notStartedCount)
                .activeAttemptsCount(activeAttemptsCount)
                .submittedCount(submittedCount)
                .disconnectedCount(disconnectedCount)
                .candidates(candidates)
                .recentEvents(recentEventDtos)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ScheduleMonitoringSummaryDto> getActiveSchedules() {
        List<ExamSchedule> schedules = scheduleRepository.findAll();
        List<ScheduleMonitoringSummaryDto> result = new ArrayList<>();

        for (ExamSchedule s : schedules) {
            long eligible = eligibilityRepository.countByScheduleIdAndEligibilityStatus(s.getId(), EligibilityStatus.ELIGIBLE);
            long active = attemptRepository.countByScheduleIdAndStatus(s.getId(), AttemptStatus.IN_PROGRESS);
            long submitted = attemptRepository.countByScheduleIdAndStatus(s.getId(), AttemptStatus.SUBMITTED);

            var exam = s.getExamination();
            result.add(ScheduleMonitoringSummaryDto.builder()
                    .scheduleId(s.getId())
                    .examinationId(exam != null ? exam.getId() : null)
                    .examinationTitle(exam != null ? exam.getTitle() : "Examination")
                    .courseCode(exam != null && exam.getCourseOffering() != null ? exam.getCourseOffering().getCourse().getCode() : "")
                    .courseName(exam != null && exam.getCourseOffering() != null ? exam.getCourseOffering().getCourse().getName() : "")
                    .scheduleStatus(s.getStatus().name())
                    .scheduledStartAt(s.getScheduledStartAt())
                    .scheduledCloseAt(s.getScheduledCloseAt())
                    .durationMinutes(s.getDurationMinutes())
                    .totalEligible((int) eligible)
                    .activeAttempts((int) active)
                    .submittedCount((int) submitted)
                    .build());
        }

        // Sort ongoing and scheduled first, then by scheduledStartAt
        result.sort((a, b) -> {
            boolean aActive = "ONGOING".equals(a.getScheduleStatus()) || "SCHEDULED".equals(a.getScheduleStatus());
            boolean bActive = "ONGOING".equals(b.getScheduleStatus()) || "SCHEDULED".equals(b.getScheduleStatus());
            if (aActive != bActive) {
                return aActive ? -1 : 1;
            }
            if (a.getScheduledStartAt() == null) return 1;
            if (b.getScheduledStartAt() == null) return -1;
            return a.getScheduledStartAt().compareTo(b.getScheduledStartAt());
        });

        return result;
    }

    public void broadcastMonitoringEvent(Long scheduleId,
                                         ExamEventType eventType,
                                         ExamAttempt attempt,
                                         String payload) {
        if (scheduleId == null || attempt == null) {
            return;
        }

        try {
            var student = attempt.getStudent();
            String studentName = (student != null && student.getUser() != null)
                    ? student.getUser().getFirstName() + " " + student.getUser().getLastName()
                    : "Student";
            String regNum = student != null ? student.getRegistrationNumber() : "";

            MonitoringEventDto eventDto = MonitoringEventDto.builder()
                    .attemptId(attempt.getId())
                    .studentId(student != null ? student.getId() : null)
                    .studentName(studentName)
                    .registrationNumber(regNum)
                    .eventType(eventType)
                    .eventPayload(payload)
                    .occurredAt(Instant.now())
                    .build();

            int answeredCount = answerRepository.countByAttemptId(attempt.getId());
            var exam = attempt.getExamination();
            int totalQ = (exam != null) ? examQuestionRepository.countByExaminationId(exam.getId()) : 0;

            boolean isOnline = attempt.getStatus() == AttemptStatus.IN_PROGRESS
                    && eventType != ExamEventType.DISCONNECTED;

            CandidateMonitoringDto candidateDto = CandidateMonitoringDto.builder()
                    .studentId(student != null ? student.getId() : null)
                    .attemptId(attempt.getId())
                    .studentName(studentName)
                    .registrationNumber(regNum)
                    .status(attempt.getStatus().name())
                    .startedAt(attempt.getStartedAt())
                    .deadlineAt(attempt.getDeadlineAt())
                    .lastActivityAt(attempt.getLastActivityAt())
                    .isOnline(isOnline)
                    .answeredQuestions(answeredCount)
                    .totalQuestions(totalQ)
                    .lastEventType(eventType.name())
                    .build();

            MonitoringWebSocketMessage wsMessage = MonitoringWebSocketMessage.builder()
                    .messageType("EVENT")
                    .scheduleId(scheduleId)
                    .event(eventDto)
                    .candidate(candidateDto)
                    .timestamp(Instant.now())
                    .build();

            messagingTemplate.convertAndSend("/topic/monitoring/schedules/" + scheduleId, wsMessage);
            log.debug("Broadcasted monitoring event to /topic/monitoring/schedules/{}: eventType={}, student={}",
                    scheduleId, eventType, regNum);
        } catch (Exception e) {
            log.warn("Failed to broadcast monitoring event to schedule {}: {}", scheduleId, e.getMessage());
        }
    }
}
