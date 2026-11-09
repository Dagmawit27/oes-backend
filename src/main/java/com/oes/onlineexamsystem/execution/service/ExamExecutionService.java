package com.oes.onlineexamsystem.execution.service;

import com.oes.onlineexamsystem.academic.entity.Student;
import com.oes.onlineexamsystem.academic.repository.StudentRepository;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.common.exception.UnauthorizedException;
import com.oes.onlineexamsystem.examination.entity.ExamQuestion;
import com.oes.onlineexamsystem.examination.entity.Examination;
import com.oes.onlineexamsystem.examination.repository.ExamQuestionRepository;
import com.oes.onlineexamsystem.execution.dto.*;
import com.oes.onlineexamsystem.execution.entity.*;
import com.oes.onlineexamsystem.execution.repository.ExamAttemptRepository;
import com.oes.onlineexamsystem.execution.repository.ExamEventRepository;
import com.oes.onlineexamsystem.execution.repository.StudentAnswerOptionRepository;
import com.oes.onlineexamsystem.execution.repository.StudentAnswerRepository;
import com.oes.onlineexamsystem.monitoring.service.ExamMonitoringService;
import com.oes.onlineexamsystem.notification.service.NotificationService;
import com.oes.onlineexamsystem.question.entity.Question;
import com.oes.onlineexamsystem.question.entity.QuestionOption;
import com.oes.onlineexamsystem.question.entity.QuestionType;
import com.oes.onlineexamsystem.question.repository.QuestionOptionRepository;
import com.oes.onlineexamsystem.result.service.GradingAndResultService;
import com.oes.onlineexamsystem.schedule.entity.EligibilityStatus;
import com.oes.onlineexamsystem.schedule.entity.ExamEligibility;
import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import com.oes.onlineexamsystem.schedule.entity.NotificationType;
import com.oes.onlineexamsystem.schedule.entity.ScheduleStatus;
import com.oes.onlineexamsystem.schedule.repository.ExamEligibilityRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class ExamExecutionService {

    private final ExamAttemptRepository attemptRepository;
    private final StudentAnswerRepository answerRepository;
    private final StudentAnswerOptionRepository answerOptionRepository;
    private final ExamEventRepository eventRepository;
    private final ExamScheduleRepository scheduleRepository;
    private final ExamEligibilityRepository eligibilityRepository;
    private final StudentRepository studentRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final GradingAndResultService gradingAndResultService;
    private final NotificationService notificationService;
    private final ExamMonitoringService monitoringService;
    private final PasscodeSecurityService passcodeSecurityService;

    public ExamExecutionService(ExamAttemptRepository attemptRepository,
                                StudentAnswerRepository answerRepository,
                                StudentAnswerOptionRepository answerOptionRepository,
                                ExamEventRepository eventRepository,
                                ExamScheduleRepository scheduleRepository,
                                ExamEligibilityRepository eligibilityRepository,
                                StudentRepository studentRepository,
                                ExamQuestionRepository examQuestionRepository,
                                QuestionOptionRepository questionOptionRepository,
                                GradingAndResultService gradingAndResultService,
                                NotificationService notificationService,
                                ExamMonitoringService monitoringService,
                                PasscodeSecurityService passcodeSecurityService) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.eventRepository = eventRepository;
        this.scheduleRepository = scheduleRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.studentRepository = studentRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.gradingAndResultService = gradingAndResultService;
        this.notificationService = notificationService;
        this.monitoringService = monitoringService;
        this.passcodeSecurityService = passcodeSecurityService;
    }

    /**
     * Start a fresh examination attempt with Passcode 1 verification,
     * or return existing active attempt if already started.
     */
    @Transactional
    public ExamExecutionPayloadDto startExam(StartExamRequest request, Long userId, String ipAddress, String userAgent) {
        Long scheduleId = request.getScheduleId();
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException("Authenticated user is not registered as a student."));

        ExamSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Schedule", "id", scheduleId));

        Examination exam = schedule.getExamination();
        Instant now = Instant.now();

        // 1. Verify schedule status
        if (schedule.getStatus() == ScheduleStatus.CANCELLED) {
            throw new BusinessRuleException("This examination schedule has been cancelled.");
        }
        if (schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new BusinessRuleException("This examination schedule has already completed.");
        }

        // 2. Authoritative Timezone and Access Window Verification
        ZoneId collegeZone = ZoneId.of(schedule.getTimezone() != null ? schedule.getTimezone() : "Africa/Addis_Ababa");
        LocalDate collegeToday = LocalDate.now(collegeZone);

        LocalDate examDate = schedule.getExaminationDate() != null
                ? schedule.getExaminationDate()
                : schedule.getScheduledStartAt().atZone(collegeZone).toLocalDate();

        if (collegeToday.isBefore(examDate)) {
            throw new BusinessRuleException("This examination is scheduled for " + examDate + " (" + schedule.getTimezone() + "). You cannot start before this date.");
        }

        if (now.isBefore(schedule.getScheduledStartAt())) {
            throw new BusinessRuleException("The examination window has not opened yet. Starts at " + schedule.getScheduledStartTime() + " (" + schedule.getTimezone() + ").");
        }

        Instant accessEndsAt = schedule.getStartAccessEndsAt() != null
                ? schedule.getStartAccessEndsAt()
                : ZonedDateTime.of(examDate, LocalTime.of(23, 59, 59), collegeZone).toInstant();

        // 3. Verify student eligibility
        ExamEligibility eligibility = eligibilityRepository.findByScheduleIdAndStudentId(schedule.getId(), student.getId())
                .orElseThrow(() -> new BusinessRuleException("You are not registered in the candidate roster for this examination."));

        if (eligibility.getEligibilityStatus() == EligibilityStatus.BLOCKED) {
            throw new BusinessRuleException("Your examination access is blocked: " +
                    (eligibility.getReason() != null ? eligibility.getReason() : "Contact academic administration"));
        }
        if (eligibility.getEligibilityStatus() == EligibilityStatus.INELIGIBLE) {
            throw new BusinessRuleException("You are not eligible to take this examination: " +
                    (eligibility.getReason() != null ? eligibility.getReason() : "Prerequisites not met"));
        }

        // 4. Check for existing attempt
        Optional<ExamAttempt> existingOpt = attemptRepository.findByStudentIdAndScheduleIdAndStatusIn(
                student.getId(), schedule.getId(),
                List.of(AttemptStatus.ACTIVE, AttemptStatus.IN_PROGRESS, AttemptStatus.CONNECTION_PAUSED, AttemptStatus.RESUME_REQUIRED)
        );

        if (existingOpt.isPresent()) {
            ExamAttempt existing = existingOpt.get();
            // If already active, refresh heartbeat timestamp
            if (existing.getStatus() == AttemptStatus.ACTIVE || existing.getStatus() == AttemptStatus.IN_PROGRESS) {
                existing.setLastHeartbeatAt(now);
                existing.setLastActivityAt(now);
                attemptRepository.save(existing);
            }
            // If attempt is CONNECTION_PAUSED or RESUME_REQUIRED, payload is returned with paused status
            // so frontend can render the paused overlay and request the required resume passcode without crashing.
            return buildExecutionPayload(existing, exam, schedule, now);
        }

        // Check if student already submitted or expired
        long finishedAttempts = attemptRepository.countByStudentIdAndExaminationIdAndStatusIn(
                student.getId(), exam.getId(), List.of(AttemptStatus.SUBMITTED, AttemptStatus.AUTO_SUBMITTED, AttemptStatus.EXPIRED)
        );
        int maxAttempts = exam.getAttemptLimit() != null ? exam.getAttemptLimit() : 1;
        if (finishedAttempts >= maxAttempts) {
            throw new BusinessRuleException("You have already completed or finalized this examination (Attempt limit reached: " + maxAttempts + ").");
        }

        // Check start window boundary: Cannot start new attempt after startAccessEndsAt
        if (now.isAfter(accessEndsAt)) {
            throw new BusinessRuleException("The examination start window closed at 23:59:59 (" + schedule.getTimezone() + "). New attempts are not permitted.");
        }

        // 5. Verify Passcode 1 for Initial Start
        passcodeSecurityService.verifyInitialStartPasscode(schedule, student, request.getPasscode(), ipAddress);

        // 6. Initialize Individual Active-Time Timer
        // Duration is strictly from Instructor's examination configuration
        int durationMinutes = exam.getDurationMinutes() != null && exam.getDurationMinutes() > 0 ? exam.getDurationMinutes() : 60;
        int durationSeconds = durationMinutes * 60;

        int attemptNumber = (int) finishedAttempts + 1;

        ExamAttempt newAttempt = ExamAttempt.builder()
                .examination(exam)
                .schedule(schedule)
                .student(student)
                .attemptNumber(attemptNumber)
                .startedAt(now)
                .activeSince(now)
                .lastHeartbeatAt(now)
                .lastActivityAt(now)
                .durationSeconds(durationSeconds)
                .remainingActiveSeconds(durationSeconds)
                .accumulatedActiveSeconds(0)
                .resumeStage(0)
                .status(AttemptStatus.ACTIVE)
                .isGraded(false)
                .build();

        ExamAttempt savedAttempt = attemptRepository.save(newAttempt);
        logEvent(savedAttempt, ExamEventType.STARTED, "Exam attempt initialized with Passcode 1", ipAddress, userAgent);
        logEvent(savedAttempt, ExamEventType.PASSCODE_VERIFIED, "Passcode 1 validated successfully", ipAddress, userAgent);

        try {
            notificationService.sendNotificationToUser(
                    student.getUser(),
                    NotificationType.EXAM_STARTING,
                    "Examination Started: " + exam.getTitle(),
                    "Your examination attempt has officially started. Active duration: " + durationMinutes + " minutes.",
                    schedule
            );
        } catch (Exception e) {
            log.warn("Failed to send start exam notification: {}", e.getMessage());
        }

        return buildExecutionPayload(savedAttempt, exam, schedule, now);
    }

    /**
     * Resume a paused examination attempt with Passcode 2 or Passcode 3 verification.
     */
    @Transactional
    public ExamExecutionPayloadDto resumeExam(ResumeExamRequest request, Long userId, String ipAddress, String userAgent) {
        Long attemptId = request.getAttemptId();
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException("Authenticated user is not registered as a student."));

        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Attempt", "id", attemptId));

        if (!attempt.getStudent().getId().equals(student.getId())) {
            throw new UnauthorizedException("Unauthorized access to this examination attempt.");
        }

        if (attempt.getStatus().isFinished()) {
            throw new BusinessRuleException("Cannot resume an examination that has already been " + attempt.getStatus());
        }

        if (attempt.getRemainingActiveSeconds() <= 0) {
            finalizeAutoSubmission(attempt, Instant.now());
            throw new BusinessRuleException("Your examination time allowance has expired.");
        }

        // Verify Passcode 2 (stage 1) or Passcode 3 (stage >= 2)
        passcodeSecurityService.verifyResumePasscode(attempt, request.getPasscode(), ipAddress);

        Instant now = Instant.now();
        attempt.setStatus(AttemptStatus.ACTIVE);
        attempt.setActiveSince(now);
        attempt.setLastHeartbeatAt(now);
        attempt.setLastResumedAt(now);
        attempt.setLastActivityAt(now);

        ExamAttempt resumed = attemptRepository.save(attempt);
        logEvent(resumed, ExamEventType.CONNECTION_RESUMED, "Attempt resumed with verified passcode at stage " + attempt.getResumeStage(), ipAddress, userAgent);
        logEvent(resumed, ExamEventType.PASSCODE_VERIFIED, "Resume passcode validated successfully", ipAddress, userAgent);

        return buildExecutionPayload(resumed, resumed.getExamination(), resumed.getSchedule(), now);
    }

    /**
     * Authoritative periodic heartbeat:
     * Decrements individual active remaining seconds and monitors connection health.
     */
    @Transactional
    public HeartbeatResponse processHeartbeat(Long attemptId, HeartbeatRequest request, Long userId, String ipAddress) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Attempt", "id", attemptId));

        if (!attempt.getStudent().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Unauthorized heartbeat access.");
        }

        Instant now = Instant.now();

        if (attempt.getStatus().isFinished()) {
            return HeartbeatResponse.builder()
                    .attemptId(attempt.getId())
                    .status(attempt.getStatus())
                    .remainingActiveSeconds(0)
                    .accumulatedActiveSeconds(attempt.getAccumulatedActiveSeconds())
                    .resumeStage(attempt.getResumeStage())
                    .isTerminated(true)
                    .serverCurrentTime(now)
                    .build();
        }

        if (attempt.getStatus() == AttemptStatus.CONNECTION_PAUSED || attempt.getStatus() == AttemptStatus.RESUME_REQUIRED) {
            return HeartbeatResponse.builder()
                    .attemptId(attempt.getId())
                    .status(attempt.getStatus())
                    .remainingActiveSeconds(attempt.getRemainingActiveSeconds())
                    .accumulatedActiveSeconds(attempt.getAccumulatedActiveSeconds())
                    .resumeStage(attempt.getResumeStage())
                    .isTerminated(false)
                    .serverCurrentTime(now)
                    .build();
        }

        // Attempt is ACTIVE (or IN_PROGRESS): Compute active elapsed interval
        Instant referenceTime = attempt.getActiveSince() != null ? attempt.getActiveSince() : attempt.getLastHeartbeatAt();
        if (referenceTime == null) {
            referenceTime = now;
        }

        long elapsedSeconds = Math.max(0, ChronoUnit.SECONDS.between(referenceTime, now));

        // Deduct active elapsed time
        int updatedRemaining = Math.max(0, attempt.getRemainingActiveSeconds() - (int) elapsedSeconds);
        attempt.setRemainingActiveSeconds(updatedRemaining);
        attempt.setAccumulatedActiveSeconds(attempt.getAccumulatedActiveSeconds() + (int) elapsedSeconds);
        attempt.setActiveSince(now);
        attempt.setLastHeartbeatAt(now);
        attempt.setLastActivityAt(now);

        // Check if individual active time has run out
        if (updatedRemaining <= 0) {
            finalizeAutoSubmission(attempt, now);
            return HeartbeatResponse.builder()
                    .attemptId(attempt.getId())
                    .status(AttemptStatus.AUTO_SUBMITTED)
                    .remainingActiveSeconds(0)
                    .accumulatedActiveSeconds(attempt.getAccumulatedActiveSeconds())
                    .resumeStage(attempt.getResumeStage())
                    .isTerminated(true)
                    .serverCurrentTime(now)
                    .build();
        }

        ExamAttempt saved = attemptRepository.save(attempt);

        return HeartbeatResponse.builder()
                .attemptId(saved.getId())
                .status(saved.getStatus())
                .remainingActiveSeconds(saved.getRemainingActiveSeconds())
                .accumulatedActiveSeconds(saved.getAccumulatedActiveSeconds())
                .resumeStage(saved.getResumeStage())
                .isTerminated(false)
                .serverCurrentTime(now)
                .build();
    }

    /**
     * Save/Autosave student answer for an individual question.
     * Concurrency-safe and idempotent.
     */
    @Transactional
    public StudentAnswerDto saveAnswer(Long attemptId, SaveAnswerRequest request, Long userId, String ipAddress, String userAgent) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Attempt", "id", attemptId));

        // 1. Verify student ownership
        if (!attempt.getStudent().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Unauthorized access to this examination attempt.");
        }

        Instant now = Instant.now();

        // 2. Verify attempt status
        if (attempt.getStatus() == AttemptStatus.CONNECTION_PAUSED || attempt.getStatus() == AttemptStatus.RESUME_REQUIRED) {
            throw new BusinessRuleException("Answers cannot be saved. Your attempt is paused due to a connection interruption. Please resume the examination.");
        }

        if (attempt.getStatus().isFinished()) {
            throw new BusinessRuleException("Answers cannot be saved. Attempt status is: " + attempt.getStatus());
        }

        // 3. Update remaining active time
        Instant refTime = attempt.getActiveSince() != null ? attempt.getActiveSince() : attempt.getLastHeartbeatAt();
        if (refTime != null) {
            long elapsed = Math.max(0, ChronoUnit.SECONDS.between(refTime, now));
            int updatedRemaining = Math.max(0, attempt.getRemainingActiveSeconds() - (int) elapsed);
            attempt.setRemainingActiveSeconds(updatedRemaining);
            attempt.setAccumulatedActiveSeconds(attempt.getAccumulatedActiveSeconds() + (int) elapsed);
            attempt.setActiveSince(now);
        }

        // 4. Verify timer hasn't expired
        if (attempt.getRemainingActiveSeconds() <= 0) {
            finalizeAutoSubmission(attempt, now);
            throw new BusinessRuleException("Examination time allowance has expired.");
        }

        attempt.setLastHeartbeatAt(now);
        attempt.setLastActivityAt(now);

        // 5. Verify question belongs to examination
        ExamQuestion examQuestion = examQuestionRepository.findById(request.getExamQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam Question", "id", request.getExamQuestionId()));

        if (!examQuestion.getExamination().getId().equals(attempt.getExamination().getId())) {
            throw new BusinessRuleException("Question does not belong to this examination.");
        }

        // 6. Validate question-type answer format & selected options
        Question question = examQuestion.getQuestion();
        List<Long> optionIds = request.getSelectedOptionIds() != null ? request.getSelectedOptionIds() : new ArrayList<>();

        if (question.getQuestionType() == QuestionType.SINGLE_CHOICE || question.getQuestionType() == QuestionType.TRUE_FALSE) {
            if (optionIds.size() > 1) {
                throw new BusinessRuleException("Single choice and True/False questions allow at most one selected option.");
            }
        }

        List<QuestionOption> validatedOptions = new ArrayList<>();
        if (!optionIds.isEmpty()) {
            for (Long optId : optionIds) {
                QuestionOption opt = questionOptionRepository.findById(optId)
                        .orElseThrow(() -> new ResourceNotFoundException("Question Option", "id", optId));
                if (!opt.getQuestion().getId().equals(question.getId())) {
                    throw new BusinessRuleException("Option ID " + optId + " does not belong to question ID " + question.getId());
                }
                validatedOptions.add(opt);
            }
        }

        // 7. Find or create StudentAnswer
        StudentAnswer answer = answerRepository.findByAttemptIdAndExamQuestionId(attempt.getId(), examQuestion.getId())
                .orElseGet(() -> StudentAnswer.builder()
                        .attempt(attempt)
                        .examQuestion(examQuestion)
                        .savedAt(now)
                        .selectedOptions(new ArrayList<>())
                        .build());

        answer.setAnswerText(request.getAnswerText() != null ? request.getAnswerText().trim() : null);
        if (request.getIsFlagged() != null) {
            answer.setIsFlagged(request.getIsFlagged());
        }
        answer.setSavedAt(now);

        if (answer.getSelectedOptions() == null) {
            answer.setSelectedOptions(new ArrayList<>());
        } else {
            answer.getSelectedOptions().clear();
        }

        for (QuestionOption opt : validatedOptions) {
            StudentAnswerOption sao = StudentAnswerOption.builder()
                    .studentAnswer(answer)
                    .questionOption(opt)
                    .build();
            answer.getSelectedOptions().add(sao);
        }

        StudentAnswer savedAnswer = answerRepository.saveAndFlush(answer);
        attemptRepository.save(attempt);

        logEvent(attempt, ExamEventType.ANSWER_SAVED, "Answer saved for question order " + examQuestion.getQuestionOrder(), ipAddress, userAgent);

        return StudentAnswerDto.fromEntity(savedAnswer);
    }

    /**
     * Submit and lock the examination attempt early.
     * Idempotent: Subsequent submissions return existing submission summary.
     */
    @Transactional
    public ExamSubmissionResultDto submitExam(Long attemptId, Long userId, String ipAddress, String userAgent) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Attempt", "id", attemptId));

        if (!attempt.getStudent().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Unauthorized access to this examination attempt.");
        }

        if (attempt.getStatus() == AttemptStatus.SUBMITTED || attempt.getStatus() == AttemptStatus.AUTO_SUBMITTED) {
            return buildSubmissionResult(attempt, "Examination was already submitted successfully.");
        }

        if (attempt.getStatus() == AttemptStatus.EXPIRED) {
            throw new BusinessRuleException("This examination attempt was finalized due to deadline expiration.");
        }

        Instant now = Instant.now();

        // Lock attempt
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(now);
        attempt.setLastActivityAt(now);

        ExamAttempt saved = attemptRepository.save(attempt);
        logEvent(saved, ExamEventType.SUBMITTED, "Exam submitted by candidate", ipAddress, userAgent);

        try {
            notificationService.sendNotificationToUser(
                    attempt.getStudent().getUser(),
                    NotificationType.EXAM_SUBMITTED,
                    "Examination Submitted: " + attempt.getExamination().getTitle(),
                    "Your examination attempt was received and recorded successfully.",
                    attempt.getSchedule()
            );
        } catch (Exception e) {
            log.warn("Failed to send submit exam notification: {}", e.getMessage());
        }

        // Auto-grade objective questions upon submission
        try {
            gradingAndResultService.autoGradeAttempt(saved);
        } catch (Exception e) {
            log.error("Failed to auto-grade attempt: {}", saved.getId(), e);
        }

        return buildSubmissionResult(saved, "Examination submitted successfully! Your responses have been finalized and recorded.");
    }

    /**
     * Log client-side telemetry events (tab blurs, resize, network status).
     */
    @Transactional
    public void logClientEvent(Long attemptId, ExamEventLogRequest request, Long userId, String ipAddress, String userAgent) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Attempt", "id", attemptId));

        if (!attempt.getStudent().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Unauthorized event logging.");
        }

        logEvent(attempt, request.getEventType(), request.getPayload(), ipAddress, userAgent);
    }

    /**
     * Background Worker 1: Detects disconnected clients (>35s missing heartbeat)
     * and transitions them to CONNECTION_PAUSED with frozen remaining time.
     */
    @Scheduled(fixedDelay = 15000) // Runs every 15 seconds
    @Transactional
    public void monitorConnectionHeartbeats() {
        Instant cutoff = Instant.now().minus(35, ChronoUnit.SECONDS);
        List<ExamAttempt> staleAttempts = attemptRepository.findByStatusInAndLastHeartbeatAtBefore(
                List.of(AttemptStatus.ACTIVE, AttemptStatus.IN_PROGRESS), cutoff
        );

        for (ExamAttempt attempt : staleAttempts) {
            Instant pauseTime = attempt.getLastHeartbeatAt() != null ? attempt.getLastHeartbeatAt() : Instant.now();
            attempt.setStatus(AttemptStatus.CONNECTION_PAUSED);
            attempt.setPausedAt(pauseTime);
            // Advance resume stage: 0 -> 1 (requires Passcode 2), 1 -> 2 (requires Passcode 3), 2+ stays at 2+
            int currentStage = attempt.getResumeStage();
            attempt.setResumeStage(currentStage == 0 ? 1 : Math.max(2, currentStage + 1));

            attemptRepository.save(attempt);
            logEvent(attempt, ExamEventType.CONNECTION_PAUSED, "Attempt paused due to heartbeat timeout (>35s)", null, null);
            logEvent(attempt, ExamEventType.HEARTBEAT_TIMEOUT, "Heartbeat ceased at " + pauseTime, null, null);
            log.info("Paused attempt {} for student {} due to heartbeat loss. Resume stage: {}",
                    attempt.getId(), attempt.getStudent().getId(), attempt.getResumeStage());
        }
    }

    /**
     * Background Worker 2: Automatically finalizes any active attempts that reached 0 remaining seconds.
     */
    @Scheduled(fixedDelay = 15000)
    @Transactional
    public void autoFinalizeZeroRemainingAttempts() {
        List<ExamAttempt> zeroRemaining = attemptRepository.findByStatusInAndRemainingActiveSecondsLessThanEqual(
                List.of(AttemptStatus.ACTIVE, AttemptStatus.IN_PROGRESS), 0
        );

        for (ExamAttempt attempt : zeroRemaining) {
            finalizeAutoSubmission(attempt, Instant.now());
        }
    }

    private void finalizeAutoSubmission(ExamAttempt attempt, Instant now) {
        attempt.setStatus(AttemptStatus.AUTO_SUBMITTED);
        attempt.setSubmittedAt(now);
        attempt.setRemainingActiveSeconds(0);
        ExamAttempt saved = attemptRepository.save(attempt);
        logEvent(saved, ExamEventType.AUTO_SUBMITTED, "Attempt auto-submitted upon timer expiration", null, null);

        try {
            gradingAndResultService.autoGradeAttempt(saved);
        } catch (Exception e) {
            log.error("Failed to auto-grade expired attempt: {}", saved.getId(), e);
        }
    }

    private ExamExecutionPayloadDto buildExecutionPayload(ExamAttempt attempt, Examination exam, ExamSchedule schedule, Instant now) {
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExaminationIdOrderByQuestionOrderAsc(exam.getId());

        List<ExamQuestionDeliveryDto> questionDtos = examQuestions.stream()
                .map(eq -> {
                    Question q = eq.getQuestion();
                    List<QuestionOptionDeliveryDto> optionDtos = questionOptionRepository.findByQuestionIdOrderByOptionOrderAsc(q.getId()).stream()
                            .map(opt -> QuestionOptionDeliveryDto.builder()
                                    .id(opt.getId())
                                    .optionOrder(opt.getOptionOrder())
                                    .optionText(opt.getOptionText())
                                    .build())
                            .toList();

                    return ExamQuestionDeliveryDto.builder()
                            .examQuestionId(eq.getId())
                            .questionId(q.getId())
                            .questionOrder(eq.getQuestionOrder())
                            .marks(eq.getMarks())
                            .questionText(q.getQuestionText())
                            .questionType(q.getQuestionType())
                            .options(optionDtos)
                            .build();
                })
                .toList();

        List<StudentAnswerDto> savedAnswers = answerRepository.findByAttemptId(attempt.getId()).stream()
                .map(StudentAnswerDto::fromEntity)
                .toList();

        return ExamExecutionPayloadDto.builder()
                .attemptId(attempt.getId())
                .examinationId(exam.getId())
                .scheduleId(schedule.getId())
                .courseCode(exam.getCourseOffering() != null && exam.getCourseOffering().getCourse() != null ? exam.getCourseOffering().getCourse().getCode() : "")
                .courseName(exam.getCourseOffering() != null && exam.getCourseOffering().getCourse() != null ? exam.getCourseOffering().getCourse().getName() : "")
                .examinationTitle(exam.getTitle())
                .examinationDescription(exam.getDescription())
                .instructions(schedule.getInstructions())
                .durationMinutes(exam.getDurationMinutes())
                .durationSeconds(attempt.getDurationSeconds())
                .remainingActiveSeconds(attempt.getRemainingActiveSeconds())
                .resumeStage(attempt.getResumeStage())
                .totalMarks(exam.getTotalMarks())
                .passingMarks(exam.getPassingMarks())
                .attemptNumber(attempt.getAttemptNumber())
                .attemptLimit(exam.getAttemptLimit())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .activeSince(attempt.getActiveSince())
                .pausedAt(attempt.getPausedAt())
                .deadlineAt(attempt.getDeadlineAt())
                .serverCurrentTime(now)
                .timezone(schedule.getTimezone())
                .randomizeQuestions(exam.getRandomizeQuestions())
                .randomizeOptions(exam.getRandomizeOptions())
                .questions(questionDtos)
                .savedAnswers(savedAnswers)
                .build();
    }

    private ExamSubmissionResultDto buildSubmissionResult(ExamAttempt attempt, String message) {
        long answeredCount = answerRepository.countByAttemptId(attempt.getId());
        long totalQuestions = examQuestionRepository.countByExaminationId(attempt.getExamination().getId());

        return ExamSubmissionResultDto.builder()
                .attemptId(attempt.getId())
                .examinationId(attempt.getExamination().getId())
                .examinationTitle(attempt.getExamination().getTitle())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .status(attempt.getStatus())
                .totalQuestions((int) totalQuestions)
                .answeredQuestions((int) answeredCount)
                .message(message)
                .build();
    }

    private void logEvent(ExamAttempt attempt, ExamEventType type, String payload, String ip, String userAgent) {
        ExamEvent event = ExamEvent.builder()
                .attempt(attempt)
                .eventType(type)
                .eventPayload(payload)
                .ipAddress(ip)
                .userAgent(userAgent)
                .occurredAt(Instant.now())
                .build();
        eventRepository.save(event);
    }
}
