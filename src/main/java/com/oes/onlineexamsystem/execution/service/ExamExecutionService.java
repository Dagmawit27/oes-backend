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
import com.oes.onlineexamsystem.schedule.entity.ExamNotification;
import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import com.oes.onlineexamsystem.schedule.entity.NotificationType;
import com.oes.onlineexamsystem.schedule.entity.ScheduleStatus;
import com.oes.onlineexamsystem.schedule.repository.ExamEligibilityRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamScheduleRepository;
import com.oes.onlineexamsystem.schedule.service.ExamScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
                                ExamMonitoringService monitoringService) {
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
    }

    /**
     * Start or resume an examination attempt for the authenticated student.
     * Idempotent: If an active attempt already exists within the deadline, it is safely returned.
     */
    @Transactional
    public ExamExecutionPayloadDto startOrResumeExam(Long scheduleId, Long userId, String ipAddress, String userAgent) {
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

        // 2. Verify schedule time window
        if (now.isAfter(schedule.getScheduledCloseAt())) {
            throw new BusinessRuleException("The examination window for this schedule closed at " + schedule.getScheduledCloseAt());
        }
        if (now.isBefore(schedule.getScheduledStartAt())) {
            throw new BusinessRuleException("The examination window has not opened yet. Starts at " + schedule.getScheduledStartAt());
        }

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

        // 4. Check for existing active attempt (Idempotent Resume)
        Optional<ExamAttempt> existingOpt = attemptRepository.findByStudentIdAndScheduleIdAndStatus(
                student.getId(), schedule.getId(), AttemptStatus.IN_PROGRESS
        );

        if (existingOpt.isPresent()) {
            ExamAttempt existing = existingOpt.get();
            // Check if deadline passed
            if (now.isAfter(existing.getDeadlineAt())) {
                existing.setStatus(AttemptStatus.EXPIRED);
                attemptRepository.save(existing);
                logEvent(existing, ExamEventType.AUTO_SUBMITTED_EXPIRED, "Attempt expired on reconnect", ipAddress, userAgent);
                throw new BusinessRuleException("Your examination deadline has expired.");
            }

            // Still active: update last activity and return
            existing.setLastActivityAt(now);
            attemptRepository.save(existing);
            logEvent(existing, ExamEventType.RECONNECTED, "Student reconnected to active attempt", ipAddress, userAgent);

            return buildExecutionPayload(existing, exam, schedule, now);
        }

        // 5. Fresh Attempt: Check attempt limits
        long completedAttempts = attemptRepository.countByStudentIdAndExaminationIdAndStatusIn(
                student.getId(), exam.getId(), List.of(AttemptStatus.SUBMITTED, AttemptStatus.EXPIRED)
        );

        int maxAttempts = exam.getAttemptLimit() != null ? exam.getAttemptLimit() : 1;
        if (completedAttempts >= maxAttempts) {
            throw new BusinessRuleException("You have reached the maximum attempt limit (" + maxAttempts + ") for this examination.");
        }

        // 6. Calculate official attempt deadline
        // Enforce: deadline = min(now + durationMinutes, scheduledCloseAt)
        Instant officialDeadline = ExamScheduleService.calculateOfficialAttemptDeadline(
                now, schedule.getDurationMinutes(), schedule.getScheduledCloseAt()
        );

        int attemptNumber = (int) completedAttempts + 1;

        ExamAttempt newAttempt = ExamAttempt.builder()
                .examination(exam)
                .schedule(schedule)
                .student(student)
                .attemptNumber(attemptNumber)
                .startedAt(now)
                .deadlineAt(officialDeadline)
                .status(AttemptStatus.IN_PROGRESS)
                .lastActivityAt(now)
                .isGraded(false)
                .build();

        ExamAttempt savedAttempt = attemptRepository.save(newAttempt);
        logEvent(savedAttempt, ExamEventType.STARTED, "Exam attempt initiated by student", ipAddress, userAgent);

        try {
            notificationService.sendNotificationToUser(
                    student.getUser(),
                    NotificationType.EXAM_STARTING,
                    "Examination Started: " + exam.getTitle(),
                    "Your examination attempt has officially started. Deadline: " + officialDeadline,
                    schedule
            );
        } catch (Exception e) {
            log.warn("Failed to send start exam notification: {}", e.getMessage());
        }

        return buildExecutionPayload(savedAttempt, exam, schedule, now);
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
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Answers cannot be saved. Attempt status is: " + attempt.getStatus());
        }

        // 3. Verify deadline
        if (now.isAfter(attempt.getDeadlineAt())) {
            attempt.setStatus(AttemptStatus.EXPIRED);
            attemptRepository.save(attempt);
            logEvent(attempt, ExamEventType.AUTO_SUBMITTED_EXPIRED, "Expired during answer save attempt", ipAddress, userAgent);
            throw new BusinessRuleException("Examination deadline has expired at " + attempt.getDeadlineAt());
        }

        attempt.setLastActivityAt(now);

        // 4. Verify question belongs to examination
        ExamQuestion examQuestion = examQuestionRepository.findById(request.getExamQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Exam Question", "id", request.getExamQuestionId()));

        if (!examQuestion.getExamination().getId().equals(attempt.getExamination().getId())) {
            throw new BusinessRuleException("Question does not belong to this examination.");
        }

        // 5. Validate question-type answer format & selected options
        Question question = examQuestion.getQuestion();
        List<Long> optionIds = request.getSelectedOptionIds() != null ? request.getSelectedOptionIds() : new ArrayList<>();

        if (question.getQuestionType() == QuestionType.SINGLE_CHOICE || question.getQuestionType() == QuestionType.TRUE_FALSE) {
            if (optionIds.size() > 1) {
                throw new BusinessRuleException("Single choice and True/False questions allow at most one selected option.");
            }
        }

        // Verify selected options belong to this question
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

        // 6. Find or create StudentAnswer
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

        // Update selected options via cascade/orphanRemoval on the entity collection
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

        logEvent(attempt, ExamEventType.ANSWER_SAVED, "Answer saved for question order " + examQuestion.getQuestionOrder(), ipAddress, userAgent);

        return StudentAnswerDto.fromEntity(savedAnswer);
    }

    /**
     * Submit and lock the examination attempt.
     * Idempotent: Subsequent submissions return existing submission summary.
     */
    @Transactional
    public ExamSubmissionResultDto submitExam(Long attemptId, Long userId, String ipAddress, String userAgent) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Attempt", "id", attemptId));

        // 1. Verify student ownership
        if (!attempt.getStudent().getUser().getId().equals(userId)) {
            throw new UnauthorizedException("Unauthorized access to this examination attempt.");
        }

        // 2. Idempotency check: Already submitted
        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            return buildSubmissionResult(attempt, "Examination was already submitted successfully.");
        }

        if (attempt.getStatus() == AttemptStatus.EXPIRED) {
            throw new BusinessRuleException("This examination attempt was finalized due to deadline expiration.");
        }

        Instant now = Instant.now();

        // 3. Lock attempt
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
     * Background worker: Automatically marks attempts as EXPIRED if the official deadline has passed.
     */
    @Scheduled(fixedDelay = 60000) // Runs every minute
    @Transactional
    public void autoFinalizeExpiredAttempts() {
        Instant now = Instant.now();
        List<ExamAttempt> expiredAttempts = attemptRepository.findByStatusAndDeadlineAtBefore(AttemptStatus.IN_PROGRESS, now);

        for (ExamAttempt attempt : expiredAttempts) {
            attempt.setStatus(AttemptStatus.EXPIRED);
            attempt.setSubmittedAt(attempt.getDeadlineAt()); // Capped at official deadline
            ExamAttempt saved = attemptRepository.save(attempt);
            logEvent(saved, ExamEventType.AUTO_SUBMITTED_EXPIRED, "System auto-finalized attempt upon deadline expiration", "SYSTEM", "BACKGROUND_SCHEDULER");
            try {
                notificationService.sendNotificationToUser(
                        attempt.getStudent().getUser(),
                        NotificationType.EXAM_COMPLETED,
                        "Examination Sitting Closed: " + attempt.getExamination().getTitle(),
                        "The examination deadline has arrived and your attempt was officially closed.",
                        attempt.getSchedule()
                );
            } catch (Exception e) {
                log.warn("Failed to send expired exam notification: {}", e.getMessage());
            }
            try {
                gradingAndResultService.autoGradeAttempt(saved);
            } catch (Exception e) {
                log.error("Failed to auto-grade expired attempt: {}", saved.getId(), e);
            }
        }
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

        if (attempt.getSchedule() != null) {
            try {
                monitoringService.broadcastMonitoringEvent(attempt.getSchedule().getId(), type, attempt, payload);
            } catch (Exception e) {
                log.warn("Failed to broadcast monitoring event to WebSocket: {}", e.getMessage());
            }
        }
    }

    /**
     * Builds student-safe examination payload.
     * NEVER sends correct answers, grading explanations, or teacher private notes.
     */
    private ExamExecutionPayloadDto buildExecutionPayload(ExamAttempt attempt, Examination exam, ExamSchedule schedule, Instant now) {
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExaminationIdOrderByQuestionOrderAsc(exam.getId());

        List<ExamQuestionDeliveryDto> questionDtos = new ArrayList<>();
        for (ExamQuestion eq : examQuestions) {
            Question q = eq.getQuestion();

            List<QuestionOptionDeliveryDto> optionDtos = new ArrayList<>();
            if (q.getOptions() != null) {
                for (QuestionOption opt : q.getOptions()) {
                    // NEVER map isCorrect or explanation to student delivery DTO!
                    optionDtos.add(QuestionOptionDeliveryDto.builder()
                            .id(opt.getId())
                            .optionOrder(opt.getOptionOrder())
                            .optionText(opt.getOptionText())
                            .build());
                }
            }

            // Randomize options if configured
            if (Boolean.TRUE.equals(exam.getRandomizeOptions())) {
                Collections.shuffle(optionDtos, new Random(attempt.getId() + eq.getId()));
            }

            questionDtos.add(ExamQuestionDeliveryDto.builder()
                    .examQuestionId(eq.getId())
                    .questionId(q.getId())
                    .questionOrder(eq.getQuestionOrder())
                    .marks(eq.getMarks())
                    .questionText(q.getQuestionText())
                    .questionType(q.getQuestionType())
                    .options(optionDtos)
                    .build());
        }

        // Randomize questions if configured (using deterministic seed per attempt)
        if (Boolean.TRUE.equals(exam.getRandomizeQuestions())) {
            Collections.shuffle(questionDtos, new Random(attempt.getId() * 31));
            // Renumber presentation order
            for (int i = 0; i < questionDtos.size(); i++) {
                questionDtos.get(i).setQuestionOrder(i + 1);
            }
        }

        // Fetch existing saved answers for restoration
        List<StudentAnswer> savedAnswers = answerRepository.findByAttemptIdWithSelectedOptions(attempt.getId());
        List<StudentAnswerDto> savedAnswerDtos = savedAnswers.stream()
                .map(StudentAnswerDto::fromEntity)
                .toList();

        return ExamExecutionPayloadDto.builder()
                .attemptId(attempt.getId())
                .examinationId(exam.getId())
                .scheduleId(schedule.getId())
                .courseCode(exam.getCourseOffering() != null ? exam.getCourseOffering().getCourse().getCode() : null)
                .courseName(exam.getCourseOffering() != null ? exam.getCourseOffering().getCourse().getName() : null)
                .examinationTitle(exam.getTitle())
                .examinationDescription(exam.getDescription())
                .instructions(exam.getInstructions())
                .durationMinutes(schedule.getDurationMinutes())
                .totalMarks(exam.getTotalMarks())
                .passingMarks(exam.getPassingMarks())
                .attemptNumber(attempt.getAttemptNumber())
                .attemptLimit(exam.getAttemptLimit())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .deadlineAt(attempt.getDeadlineAt())
                .serverCurrentTime(now)
                .randomizeQuestions(exam.getRandomizeQuestions())
                .randomizeOptions(exam.getRandomizeOptions())
                .questions(questionDtos)
                .savedAnswers(savedAnswerDtos)
                .build();
    }

    private ExamSubmissionResultDto buildSubmissionResult(ExamAttempt attempt, String message) {
        int totalQuestions = examQuestionRepository.findByExaminationIdOrderByQuestionOrderAsc(attempt.getExamination().getId()).size();
        int answeredQuestions = answerRepository.findByAttemptId(attempt.getId()).size();

        return ExamSubmissionResultDto.builder()
                .attemptId(attempt.getId())
                .examinationId(attempt.getExamination().getId())
                .examinationTitle(attempt.getExamination().getTitle())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .totalQuestions(totalQuestions)
                .answeredQuestions(answeredQuestions)
                .message(message)
                .build();
    }
}
