package com.oes.onlineexamsystem.monitoring.service;

import com.oes.onlineexamsystem.academic.entity.Student;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
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
import com.oes.onlineexamsystem.result.entity.ExamResult;
import com.oes.onlineexamsystem.result.entity.GradingStatus;
import com.oes.onlineexamsystem.result.entity.PublicationStatus;
import com.oes.onlineexamsystem.result.repository.ExamResultRepository;
import com.oes.onlineexamsystem.result.repository.GradeDetailRepository;
import com.oes.onlineexamsystem.schedule.entity.*;
import com.oes.onlineexamsystem.schedule.repository.ExamEligibilityRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamNotificationRepository;
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

    private static final Set<AttemptStatus> ACTIVE_ATTEMPT_STATUSES = EnumSet.of(
            AttemptStatus.ACTIVE,
            AttemptStatus.IN_PROGRESS,
            AttemptStatus.CONNECTION_PAUSED,
            AttemptStatus.RESUME_REQUIRED
    );

    private static final Set<AttemptStatus> FINISHED_SUBMITTED_STATUSES = EnumSet.of(
            AttemptStatus.SUBMITTED,
            AttemptStatus.AUTO_SUBMITTED
    );

    private final ExamScheduleRepository scheduleRepository;
    private final ExamEligibilityRepository eligibilityRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamEventRepository eventRepository;
    private final StudentAnswerRepository answerRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamResultRepository resultRepository;
    private final GradeDetailRepository gradeDetailRepository;
    private final ExamNotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ExamMonitoringService(ExamScheduleRepository scheduleRepository,
                                 ExamEligibilityRepository eligibilityRepository,
                                 ExamAttemptRepository attemptRepository,
                                 ExamEventRepository eventRepository,
                                 StudentAnswerRepository answerRepository,
                                 ExamQuestionRepository examQuestionRepository,
                                 ExamResultRepository resultRepository,
                                 GradeDetailRepository gradeDetailRepository,
                                 ExamNotificationRepository notificationRepository,
                                 SimpMessagingTemplate messagingTemplate) {
        this.scheduleRepository = scheduleRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.attemptRepository = attemptRepository;
        this.eventRepository = eventRepository;
        this.answerRepository = answerRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.resultRepository = resultRepository;
        this.gradeDetailRepository = gradeDetailRepository;
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public ExamMonitoringSnapshotDto getScheduleSnapshot(Long scheduleId) {
        ExamSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam schedule not found with id: " + scheduleId));

        var exam = schedule.getExamination();
        Long examId = exam != null ? exam.getId() : null;
        int configuredQuestions = examId != null ? examQuestionRepository.countByExaminationId(examId) : 0;

        List<ExamEligibility> eligibilities = eligibilityRepository.findByScheduleIdOrderByStudentRegistrationNumberAsc(scheduleId);
        List<ExamAttempt> scheduleAttempts = attemptRepository.findByScheduleId(scheduleId);

        // Prefer active or most recent attempt per student on this schedule
        Map<Long, ExamAttempt> studentAttemptMap = new HashMap<>();
        for (ExamAttempt attempt : scheduleAttempts) {
            Long sid = attempt.getStudent().getId();
            ExamAttempt existing = studentAttemptMap.get(sid);
            if (existing == null || isHigherPriorityAttempt(attempt, existing)) {
                studentAttemptMap.put(sid, attempt);
            }
        }

        // Load examination-level results & schedule notifications for accurate historical/fallback reconciliation
        Map<Long, ExamResult> resultByStudentId = examId != null
                ? resultRepository.findByExaminationId(examId).stream()
                        .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a))
                : Collections.emptyMap();

        List<ExamNotification> examNotifications = examId != null
                ? notificationRepository.findBySchedule_ExaminationId(examId)
                : notificationRepository.findByScheduleIdOrderByCreatedAtDesc(scheduleId);

        List<ExamEvent> recentEventsList = eventRepository.findTop50ByAttempt_ScheduleIdOrderByOccurredAtDesc(scheduleId);
        Map<Long, ExamEvent> latestEventByAttempt = new HashMap<>();
        for (ExamEvent ev : recentEventsList) {
            latestEventByAttempt.putIfAbsent(ev.getAttempt().getId(), ev);
        }

        // Build unified student directory (eligibilities + any attempt holders)
        Map<Long, Student> studentMap = new LinkedHashMap<>();
        for (ExamEligibility el : eligibilities) {
            if (el.getStudent() != null) {
                studentMap.putIfAbsent(el.getStudent().getId(), el.getStudent());
            }
        }
        for (ExamAttempt attempt : scheduleAttempts) {
            if (attempt.getStudent() != null) {
                studentMap.putIfAbsent(attempt.getStudent().getId(), attempt.getStudent());
            }
        }

        Map<Long, Student> studentByUserId = new HashMap<>();
        for (Student st : studentMap.values()) {
            if (st.getUser() != null) {
                studentByUserId.put(st.getUser().getId(), st);
            }
        }

        // Group schedule-scoped notifications by userId
        Map<Long, List<ExamNotification>> scheduleNotifsByUser = new HashMap<>();
        List<ExamNotification> scheduleNotifications = new ArrayList<>();
        for (ExamNotification notif : examNotifications) {
            if (notif.getSchedule() != null && Objects.equals(notif.getSchedule().getId(), scheduleId)) {
                scheduleNotifications.add(notif);
                if (notif.getUser() != null) {
                    scheduleNotifsByUser.computeIfAbsent(notif.getUser().getId(), k -> new ArrayList<>()).add(notif);
                }
            }
        }

        Instant now = Instant.now();
        boolean sittingClosed = isSittingClosed(schedule, now);
        List<CandidateMonitoringDto> candidates = new ArrayList<>();

        int startedCount = 0;
        int activeAttemptsCount = 0;
        int submittedCount = 0;
        int disconnectedCount = 0;

        for (Student student : studentMap.values()) {
            ExamAttempt attempt = studentAttemptMap.get(student.getId());
            ExamResult result = resultByStudentId.get(student.getId());
            Long userId = student.getUser() != null ? student.getUser().getId() : null;
            List<ExamNotification> studentScheduleNotifs = userId != null
                    ? scheduleNotifsByUser.getOrDefault(userId, Collections.emptyList())
                    : Collections.emptyList();

            int gradeDetailsCount = result != null ? gradeDetailRepository.findByExamResultId(result.getId()).size() : 0;
            int totalQuestions = Math.max(configuredQuestions, gradeDetailsCount);

            if (attempt != null) {
                startedCount++;
                AttemptStatus st = attempt.getStatus();
                boolean isAttemptActive = ACTIVE_ATTEMPT_STATUSES.contains(st);
                boolean isAttemptSubmitted = FINISHED_SUBMITTED_STATUSES.contains(st)
                        || (st == AttemptStatus.EXPIRED && hasRecordedSubmission(result));

                if (isAttemptActive) {
                    activeAttemptsCount++;
                } else if (isAttemptSubmitted) {
                    submittedCount++;
                }

                ExamEvent latestEvent = latestEventByAttempt.get(attempt.getId());
                boolean isOnline = st.isActive()
                        && attempt.getLastActivityAt() != null
                        && Duration.between(attempt.getLastActivityAt(), now).compareTo(INACTIVE_THRESHOLD) <= 0
                        && (latestEvent == null || latestEvent.getEventType() != ExamEventType.DISCONNECTED);

                if (isAttemptActive && !isOnline) {
                    disconnectedCount++;
                }

                int answeredCount = answerRepository.countByAttemptId(attempt.getId());
                if (answeredCount == 0 && gradeDetailsCount > 0) {
                    answeredCount = gradeDetailsCount;
                }

                candidates.add(CandidateMonitoringDto.builder()
                        .studentId(student.getId())
                        .attemptId(attempt.getId())
                        .studentName(getStudentFullName(student))
                        .registrationNumber(student.getRegistrationNumber())
                        .email(userId != null ? student.getUser().getEmail() : null)
                        .status(st.name())
                        .startedAt(attempt.getStartedAt())
                        .deadlineAt(attempt.getDeadlineAt())
                        .lastActivityAt(attempt.getLastActivityAt())
                        .isOnline(isOnline)
                        .answeredQuestions(answeredCount)
                        .totalQuestions(totalQuestions)
                        .lastEventType(latestEvent != null ? latestEvent.getEventType().name() : "STARTED")
                        .build());
            } else if (hasRecordedSubmission(result)
                    && shouldAttributeResultToSchedule(scheduleId, userId, examNotifications, scheduleAttempts, result)) {
                startedCount++;
                submittedCount++;

                Instant startTs = findEarliestNotifTime(studentScheduleNotifs, NotificationType.EXAM_STARTING);
                if (startTs == null) {
                    startTs = result.getCreatedAt();
                }
                Instant submitTs = findLatestNotifTime(studentScheduleNotifs,
                        NotificationType.EXAM_SUBMITTED, NotificationType.EXAM_COMPLETED);
                if (submitTs == null) {
                    submitTs = result.getGradedAt() != null ? result.getGradedAt() : result.getCreatedAt();
                }

                int answered = gradeDetailsCount > 0 ? gradeDetailsCount : totalQuestions;

                candidates.add(CandidateMonitoringDto.builder()
                        .studentId(student.getId())
                        .studentName(getStudentFullName(student))
                        .registrationNumber(student.getRegistrationNumber())
                        .email(userId != null ? student.getUser().getEmail() : null)
                        .status(AttemptStatus.SUBMITTED.name())
                        .startedAt(startTs)
                        .deadlineAt(schedule.getScheduledCloseAt())
                        .lastActivityAt(submitTs)
                        .isOnline(false)
                        .answeredQuestions(answered)
                        .totalQuestions(totalQuestions)
                        .lastEventType(ExamEventType.SUBMITTED.name())
                        .build());
            } else if (hasNotifType(studentScheduleNotifs, NotificationType.EXAM_STARTING)) {
                startedCount++;
                Instant startTs = findEarliestNotifTime(studentScheduleNotifs, NotificationType.EXAM_STARTING);
                String fallbackStatus = sittingClosed ? AttemptStatus.EXPIRED.name() : AttemptStatus.ACTIVE.name();
                if (!sittingClosed) {
                    activeAttemptsCount++;
                }

                candidates.add(CandidateMonitoringDto.builder()
                        .studentId(student.getId())
                        .studentName(getStudentFullName(student))
                        .registrationNumber(student.getRegistrationNumber())
                        .email(userId != null ? student.getUser().getEmail() : null)
                        .status(fallbackStatus)
                        .startedAt(startTs)
                        .deadlineAt(schedule.getScheduledCloseAt())
                        .lastActivityAt(startTs)
                        .isOnline(false)
                        .answeredQuestions(0)
                        .totalQuestions(totalQuestions)
                        .lastEventType(ExamEventType.STARTED.name())
                        .build());
            } else {
                candidates.add(CandidateMonitoringDto.builder()
                        .studentId(student.getId())
                        .studentName(getStudentFullName(student))
                        .registrationNumber(student.getRegistrationNumber())
                        .email(userId != null ? student.getUser().getEmail() : null)
                        .status(AttemptStatus.NOT_STARTED.name())
                        .isOnline(false)
                        .answeredQuestions(0)
                        .totalQuestions(totalQuestions)
                        .lastEventType(AttemptStatus.NOT_STARTED.name())
                        .build());
            }
        }

        int totalEligible = eligibilities.size() > 0 ? eligibilities.size() : studentMap.size();
        int notStartedCount = Math.max(0, totalEligible - startedCount);

        List<MonitoringEventDto> recentEventDtos = buildActivityHistory(
                schedule, recentEventsList, scheduleNotifications, studentByUserId, resultByStudentId, examNotifications, scheduleAttempts
        );

        return ExamMonitoringSnapshotDto.builder()
                .scheduleId(schedule.getId())
                .examinationId(examId)
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
            List<ExamEligibility> eligibilities = eligibilityRepository.findByScheduleId(s.getId());
            long eligibleCount = eligibilities.stream()
                    .filter(e -> e.getEligibilityStatus() == EligibilityStatus.ELIGIBLE
                            || e.getEligibilityStatus() == EligibilityStatus.EXEMPTED)
                    .count();

            List<ExamAttempt> attempts = attemptRepository.findByScheduleId(s.getId());
            long activeCount = attempts.stream()
                    .filter(a -> ACTIVE_ATTEMPT_STATUSES.contains(a.getStatus()))
                    .map(a -> a.getStudent().getId())
                    .distinct()
                    .count();

            Set<Long> submittedStudentIds = attempts.stream()
                    .filter(a -> FINISHED_SUBMITTED_STATUSES.contains(a.getStatus()))
                    .map(a -> a.getStudent().getId())
                    .collect(Collectors.toSet());

            var exam = s.getExamination();
            if (exam != null) {
                List<ExamResult> examResults = resultRepository.findByExaminationId(exam.getId());
                if (!examResults.isEmpty()) {
                    List<ExamNotification> examNotifications = notificationRepository.findBySchedule_ExaminationId(exam.getId());
                    Set<Long> eligibleIds = eligibilities.stream()
                            .filter(e -> e.getStudent() != null)
                            .map(e -> e.getStudent().getId())
                            .collect(Collectors.toSet());

                    for (ExamResult er : examResults) {
                        Long sid = er.getStudent().getId();
                        if (!submittedStudentIds.contains(sid)
                                && eligibleIds.contains(sid)
                                && hasRecordedSubmission(er)) {
                            Long uid = er.getStudent().getUser() != null ? er.getStudent().getUser().getId() : null;
                            if (shouldAttributeResultToSchedule(s.getId(), uid, examNotifications, attempts, er)) {
                                submittedStudentIds.add(sid);
                            }
                        }
                    }
                }
            }

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
                    .totalEligible((int) eligibleCount)
                    .activeAttempts((int) activeCount)
                    .submittedCount(submittedStudentIds.size())
                    .build());
        }

        // Sort: 0 = currently live/ongoing, 1 = upcoming (earliest start first), 2 = already passed/closed/cancelled (most recent first)
        Instant now = Instant.now();
        result.sort((a, b) -> {
            int aTier = monitoringSortTier(a, now);
            int bTier = monitoringSortTier(b, now);
            if (aTier != bTier) {
                return Integer.compare(aTier, bTier);
            }
            Instant aStart = a.getScheduledStartAt();
            Instant bStart = b.getScheduledStartAt();
            if (aStart == null) return 1;
            if (bStart == null) return -1;
            return aTier < 2 ? aStart.compareTo(bStart) : bStart.compareTo(aStart);
        });

        return result;
    }

    private int monitoringSortTier(ScheduleMonitoringSummaryDto s, Instant now) {
        String status = s.getScheduleStatus();
        if ("CANCELLED".equals(status)) {
            return 2;
        }
        boolean passedByTime = s.getScheduledCloseAt() != null && !s.getScheduledCloseAt().isAfter(now);
        if ("COMPLETED".equals(status) || passedByTime) {
            return 2;
        }
        if ("ONGOING".equals(status) || (s.getScheduledStartAt() != null && !s.getScheduledStartAt().isAfter(now))) {
            return 0;
        }
        return 1;
    }

    @Transactional(readOnly = true)
    public void verifyLiveAnnouncementAllowed(Long scheduleId) {
        ExamSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam schedule not found with id: " + scheduleId));
        if (isSittingClosed(schedule, Instant.now())) {
            throw new BusinessRuleException("Cannot send announcements for a closed or completed examination sitting.");
        }
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
            String studentName = getStudentFullName(student);
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

            boolean isOnline = attempt.getStatus().isActive()
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

    private List<MonitoringEventDto> buildActivityHistory(
            ExamSchedule schedule,
            List<ExamEvent> rawEvents,
            List<ExamNotification> scheduleNotifications,
            Map<Long, Student> studentByUserId,
            Map<Long, ExamResult> resultByStudentId,
            List<ExamNotification> allExamNotifications,
            List<ExamAttempt> scheduleAttempts) {

        List<MonitoringEventDto> combined = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (ExamEvent ev : rawEvents) {
            Student st = ev.getAttempt().getStudent();
            Long stId = st != null ? st.getId() : null;
            Instant ts = ev.getOccurredAt();
            combined.add(MonitoringEventDto.builder()
                    .id(ev.getId())
                    .attemptId(ev.getAttempt().getId())
                    .studentId(stId)
                    .studentName(getStudentFullName(st))
                    .registrationNumber(st != null ? st.getRegistrationNumber() : "")
                    .eventType(ev.getEventType())
                    .eventPayload(ev.getEventPayload())
                    .occurredAt(ts)
                    .build());
            seenKeys.add(dedupKey(stId, ev.getEventType()));
        }

        // Supplement with recorded schedule lifecycle notifications when telemetry events are sparse/absent
        for (ExamNotification notif : scheduleNotifications) {
            if (notif.getUser() == null) continue;
            Student st = studentByUserId.get(notif.getUser().getId());
            if (st == null) continue;

            ExamEventType mappedType = mapNotificationToEventType(notif.getEventType());
            if (mappedType == null) continue;

            // Avoid duplicating live ExamEvent entries already captured for STARTED/SUBMITTED
            if (mappedType == ExamEventType.STARTED && seenKeys.contains(dedupKey(st.getId(), ExamEventType.STARTED))) {
                continue;
            }
            if (notif.getEventType() != NotificationType.RESULTS_PUBLISHED
                    && seenKeys.contains(dedupKey(st.getId(), mappedType))) {
                continue;
            }

            String payload = notif.getEventType() == NotificationType.RESULTS_PUBLISHED
                    ? "Official results published"
                    : notif.getMessage();

            combined.add(MonitoringEventDto.builder()
                    .studentId(st.getId())
                    .studentName(getStudentFullName(st))
                    .registrationNumber(st.getRegistrationNumber())
                    .eventType(mappedType)
                    .eventPayload(payload)
                    .occurredAt(notif.getCreatedAt())
                    .build());

            if (notif.getEventType() != NotificationType.RESULTS_PUBLISHED) {
                seenKeys.add(dedupKey(st.getId(), mappedType));
            }
        }

        // Ensure recorded exam results (with no matching SUBMITTED notification/event) also appear in history
        for (ExamResult er : resultByStudentId.values()) {
            Student st = er.getStudent();
            if (st == null || !studentByUserId.containsValue(st) || !hasRecordedSubmission(er)) {
                continue;
            }
            Long uid = st.getUser() != null ? st.getUser().getId() : null;
            if (!shouldAttributeResultToSchedule(schedule.getId(), uid, allExamNotifications, scheduleAttempts, er)) {
                continue;
            }
            if (!seenKeys.contains(dedupKey(st.getId(), ExamEventType.SUBMITTED))) {
                Instant ts = er.getGradedAt() != null ? er.getGradedAt() : er.getCreatedAt();
                String scoreSummary = er.getEarnedMarks() != null
                        ? "Submission recorded & graded (" + er.getEarnedMarks() + " / " + er.getTotalMarks() + ")"
                        : "Submission recorded";
                combined.add(MonitoringEventDto.builder()
                        .studentId(st.getId())
                        .studentName(getStudentFullName(st))
                        .registrationNumber(st.getRegistrationNumber())
                        .eventType(ExamEventType.SUBMITTED)
                        .eventPayload(scoreSummary)
                        .occurredAt(ts)
                        .build());
                seenKeys.add(dedupKey(st.getId(), ExamEventType.SUBMITTED));
            }
        }

        combined.sort((a, b) -> {
            if (a.getOccurredAt() == null) return 1;
            if (b.getOccurredAt() == null) return -1;
            return b.getOccurredAt().compareTo(a.getOccurredAt());
        });

        return combined.size() > 100 ? combined.subList(0, 100) : combined;
    }

    private ExamEventType mapNotificationToEventType(NotificationType type) {
        if (type == null) return null;
        return switch (type) {
            case EXAM_STARTING -> ExamEventType.STARTED;
            case EXAM_SUBMITTED, EXAM_COMPLETED, RESULTS_PUBLISHED -> ExamEventType.SUBMITTED;
            default -> null;
        };
    }

    private String dedupKey(Long studentId, ExamEventType type) {
        return studentId + ":" + (type != null ? type.name() : "");
    }

    private boolean isHigherPriorityAttempt(ExamAttempt candidate, ExamAttempt current) {
        boolean candActive = ACTIVE_ATTEMPT_STATUSES.contains(candidate.getStatus());
        boolean currActive = ACTIVE_ATTEMPT_STATUSES.contains(current.getStatus());
        if (candActive != currActive) return candActive;
        Instant cTime = candidate.getStartedAt();
        Instant eTime = current.getStartedAt();
        if (cTime == null) return false;
        if (eTime == null) return true;
        return cTime.isAfter(eTime);
    }

    private boolean hasRecordedSubmission(ExamResult result) {
        if (result == null) return false;
        return result.getEarnedMarks() != null
                || result.getGradingStatus() != GradingStatus.PENDING
                || result.getPublicationStatus() == PublicationStatus.PUBLISHED;
    }

    private boolean shouldAttributeResultToSchedule(
            Long scheduleId,
            Long userId,
            List<ExamNotification> examNotifications,
            List<ExamAttempt> scheduleAttempts,
            ExamResult result) {

        if (result.getAttempt() != null && result.getAttempt().getSchedule() != null) {
            return Objects.equals(result.getAttempt().getSchedule().getId(), scheduleId);
        }
        // If student had attempts directly on this schedule, use this schedule
        if (scheduleAttempts.stream().anyMatch(a -> a.getStudent() != null && Objects.equals(a.getStudent().getId(), result.getStudent().getId()))) {
            return true;
        }
        if (userId != null) {
            boolean hasSubmitHere = examNotifications.stream().anyMatch(n ->
                    n.getSchedule() != null && Objects.equals(n.getSchedule().getId(), scheduleId)
                            && n.getUser() != null && Objects.equals(n.getUser().getId(), userId)
                            && (n.getEventType() == NotificationType.EXAM_SUBMITTED || n.getEventType() == NotificationType.EXAM_COMPLETED));
            if (hasSubmitHere) return true;

            boolean hasSubmitElsewhere = examNotifications.stream().anyMatch(n ->
                    n.getSchedule() != null && !Objects.equals(n.getSchedule().getId(), scheduleId)
                            && n.getUser() != null && Objects.equals(n.getUser().getId(), userId)
                            && (n.getEventType() == NotificationType.EXAM_SUBMITTED || n.getEventType() == NotificationType.EXAM_COMPLETED));
            if (hasSubmitElsewhere) return false;

            boolean hasStartHere = examNotifications.stream().anyMatch(n ->
                    n.getSchedule() != null && Objects.equals(n.getSchedule().getId(), scheduleId)
                            && n.getUser() != null && Objects.equals(n.getUser().getId(), userId)
                            && n.getEventType() == NotificationType.EXAM_STARTING);
            if (hasStartHere) return true;
        }
        return true;
    }

    private boolean hasNotifType(List<ExamNotification> notifications, NotificationType type) {
        return notifications.stream().anyMatch(n -> n.getEventType() == type);
    }

    private Instant findEarliestNotifTime(List<ExamNotification> notifications, NotificationType type) {
        return notifications.stream()
                .filter(n -> n.getEventType() == type && n.getCreatedAt() != null)
                .map(ExamNotification::getCreatedAt)
                .min(Instant::compareTo)
                .orElse(null);
    }

    private Instant findLatestNotifTime(List<ExamNotification> notifications, NotificationType... types) {
        Set<NotificationType> set = EnumSet.copyOf(Arrays.asList(types));
        return notifications.stream()
                .filter(n -> set.contains(n.getEventType()) && n.getCreatedAt() != null)
                .map(ExamNotification::getCreatedAt)
                .max(Instant::compareTo)
                .orElse(null);
    }

    private boolean isSittingClosed(ExamSchedule schedule, Instant now) {
        return schedule.getStatus() == ScheduleStatus.COMPLETED
                || schedule.getStatus() == ScheduleStatus.CANCELLED
                || (schedule.getScheduledCloseAt() != null && !schedule.getScheduledCloseAt().isAfter(now));
    }

    private String getStudentFullName(Student student) {
        if (student == null || student.getUser() == null) return "Student";
        return student.getUser().getFirstName() + " " + student.getUser().getLastName();
    }
}
