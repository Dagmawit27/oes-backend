package com.oes.onlineexamsystem.result.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.academic.entity.Course;
import com.oes.onlineexamsystem.academic.entity.CourseOffering;
import com.oes.onlineexamsystem.academic.entity.Enrollment;
import com.oes.onlineexamsystem.academic.entity.EnrollmentStatus;
import com.oes.onlineexamsystem.academic.entity.Instructor;
import com.oes.onlineexamsystem.academic.entity.Student;
import com.oes.onlineexamsystem.academic.repository.CourseOfferingRepository;
import com.oes.onlineexamsystem.academic.repository.EnrollmentRepository;
import com.oes.onlineexamsystem.academic.repository.InstructorRepository;
import com.oes.onlineexamsystem.academic.repository.StudentRepository;
import com.oes.onlineexamsystem.academic.service.InstructorService;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.common.exception.UnauthorizedException;
import com.oes.onlineexamsystem.examination.dto.ExaminationDto;
import com.oes.onlineexamsystem.examination.entity.ExamQuestion;
import com.oes.onlineexamsystem.examination.entity.ExamStatus;
import com.oes.onlineexamsystem.examination.entity.Examination;
import com.oes.onlineexamsystem.examination.repository.ExamQuestionRepository;
import com.oes.onlineexamsystem.examination.repository.ExaminationRepository;
import com.oes.onlineexamsystem.execution.entity.AttemptStatus;
import com.oes.onlineexamsystem.execution.entity.ExamAttempt;
import com.oes.onlineexamsystem.execution.entity.StudentAnswer;
import com.oes.onlineexamsystem.execution.entity.StudentAnswerOption;
import com.oes.onlineexamsystem.execution.repository.ExamAttemptRepository;
import com.oes.onlineexamsystem.execution.repository.StudentAnswerRepository;
import com.oes.onlineexamsystem.notification.service.NotificationService;
import com.oes.onlineexamsystem.question.entity.Question;
import com.oes.onlineexamsystem.question.entity.QuestionOption;
import com.oes.onlineexamsystem.question.entity.QuestionType;
import com.oes.onlineexamsystem.question.repository.QuestionRepository;
import com.oes.onlineexamsystem.result.dto.*;
import com.oes.onlineexamsystem.result.entity.*;
import com.oes.onlineexamsystem.result.repository.ExamResultRepository;
import com.oes.onlineexamsystem.result.repository.GradeDetailRepository;
import com.oes.onlineexamsystem.result.repository.ResultCorrectionAuditRepository;
import com.oes.onlineexamsystem.schedule.entity.ExamEligibility;
import com.oes.onlineexamsystem.schedule.entity.NotificationType;
import com.oes.onlineexamsystem.schedule.repository.ExamEligibilityRepository;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GradingAndResultService {

    private final ExamResultRepository resultRepository;
    private final GradeDetailRepository gradeDetailRepository;
    private final ResultCorrectionAuditRepository correctionAuditRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExaminationRepository examinationRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final StudentAnswerRepository answerRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final ExamEligibilityRepository eligibilityRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final QuestionRepository questionRepository;
    private final InstructorRepository instructorRepository;
    private final InstructorService instructorService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Automatically grade all auto-gradable questions (SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE)
     * for a submitted exam attempt. Idempotent and thread-safe.
     */
    @Transactional
    public ExamResult autoGradeAttempt(ExamAttempt attempt) {
        Examination exam = attempt.getExamination();
        Student student = attempt.getStudent();

        // 1. Find or create ExamResult
        ExamResult result = resultRepository.findByExaminationIdAndStudentId(exam.getId(), student.getId())
                .orElseGet(() -> ExamResult.builder()
                        .examination(exam)
                        .student(student)
                        .attempt(attempt)
                        .totalMarks(exam.getTotalMarks())
                        .gradingStatus(GradingStatus.PENDING)
                        .publicationStatus(PublicationStatus.DRAFT)
                        .build());

        result.setAttempt(attempt);
        result.setTotalMarks(exam.getTotalMarks());
        ExamResult savedResult = resultRepository.save(result);

        // 2. Fetch all student answers for this attempt
        List<StudentAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        Map<Long, StudentAnswer> answerMap = answers.stream()
                .collect(Collectors.toMap(a -> a.getExamQuestion().getId(), a -> a, (a1, a2) -> a1));

        // 3. Process each question
        List<ExamQuestion> examQuestions = examQuestionRepository.findByExaminationIdOrderByQuestionOrderAsc(exam.getId());
        boolean hasUngradedQuestions = false;
        boolean hasMissingMaxMarks = false;

        for (ExamQuestion eq : examQuestions) {
            GradeDetail detail = gradeDetailRepository.findByExamResultIdAndExamQuestionId(savedResult.getId(), eq.getId())
                    .orElseGet(() -> GradeDetail.builder()
                            .examResult(savedResult)
                            .examQuestion(eq)
                            .maximumMarks(eq.getMarks())
                            .build());

            detail.setMaximumMarks(eq.getMarks());
            StudentAnswer answer = answerMap.get(eq.getId());
            detail.setStudentAnswer(answer);

            if (eq.getMarks() == null) {
                hasMissingMaxMarks = true;
            }

            Question question = eq.getQuestion();
            QuestionType qType = question.getQuestionType();

            // Automatic grading for objective question types
            if (qType == QuestionType.SINGLE_CHOICE || qType == QuestionType.TRUE_FALSE) {
                detail.setIsAutoGraded(true);
                Long correctOptionId = getCorrectOptionIdFromQuestion(question);

                if (answer != null && answer.getSelectedOptions() != null && !answer.getSelectedOptions().isEmpty()) {
                    Long selectedId = answer.getSelectedOptions().get(0).getQuestionOption().getId();
                    if (correctOptionId != null && correctOptionId.equals(selectedId)) {
                        detail.setAwardedMarks(eq.getMarks() != null ? eq.getMarks() : null);
                    } else {
                        detail.setAwardedMarks(BigDecimal.ZERO);
                    }
                } else {
                    // Unanswered in submitted attempt receives 0
                    detail.setAwardedMarks(BigDecimal.ZERO);
                }
                detail.setGradedAt(Instant.now());
            } else if (qType == QuestionType.MULTIPLE_CHOICE) {
                detail.setIsAutoGraded(true);
                Set<Long> correctOptionIds = getCorrectOptionIdsFromQuestion(question);
                Set<Long> selectedOptionIds = new HashSet<>();

                if (answer != null && answer.getSelectedOptions() != null) {
                    for (StudentAnswerOption sao : answer.getSelectedOptions()) {
                        selectedOptionIds.add(sao.getQuestionOption().getId());
                    }
                }

                if (!correctOptionIds.isEmpty() && correctOptionIds.equals(selectedOptionIds)) {
                    detail.setAwardedMarks(eq.getMarks() != null ? eq.getMarks() : null);
                } else {
                    detail.setAwardedMarks(BigDecimal.ZERO);
                }
                detail.setGradedAt(Instant.now());
            } else {
                // Manual grading required (SHORT_ANSWER, ESSAY)
                detail.setIsAutoGraded(false);
                // Preserve existing manual grade if present, otherwise null
                if (detail.getAwardedMarks() == null) {
                    hasUngradedQuestions = true;
                }
            }

            gradeDetailRepository.save(detail);
        }

        // 4. Update overall result status
        List<GradeDetail> allGrades = gradeDetailRepository.findByExamResultId(savedResult.getId());
        boolean allGraded = allGrades.stream().allMatch(g -> g.getAwardedMarks() != null);

        if (allGraded && !hasMissingMaxMarks) {
            BigDecimal earnedTotal = allGrades.stream()
                    .map(GradeDetail::getAwardedMarks)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            savedResult.setEarnedMarks(earnedTotal);
            savedResult.setGradingStatus(GradingStatus.COMPLETED);
            savedResult.setGradedAt(Instant.now());

            attempt.setIsGraded(true);
            attempt.setTotalScore(earnedTotal);
            attemptRepository.save(attempt);
        } else {
            savedResult.setEarnedMarks(null);
            savedResult.setGradingStatus(GradingStatus.PENDING);
        }

        return resultRepository.save(savedResult);
    }

    /**
     * Retrieve full grading canvas for an instructor to view / grade a student's attempt.
     */
    @Transactional
    public AttemptGradingDto getAttemptGrading(Long attemptId, Long userId, boolean isAdmin, boolean isOfficer) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Attempt", "id", attemptId));

        Examination exam = attempt.getExamination();
        verifyInstructorOrOfficerAccess(exam, userId, isAdmin, isOfficer);

        // Ensure result and grades are initialized
        ExamResult result = autoGradeAttempt(attempt);
        List<GradeDetail> gradeDetails = gradeDetailRepository.findByExamResultId(result.getId());
        Map<Long, GradeDetail> gradeMap = gradeDetails.stream()
                .collect(Collectors.toMap(g -> g.getExamQuestion().getId(), g -> g));

        List<ExamQuestion> questions = examQuestionRepository.findByExaminationIdOrderByQuestionOrderAsc(exam.getId());
        List<QuestionGradingItemDto> questionDtos = new ArrayList<>();

        for (ExamQuestion eq : questions) {
            GradeDetail gd = gradeMap.get(eq.getId());
            StudentAnswer sa = gd != null ? gd.getStudentAnswer() : null;

            Question q = eq.getQuestion();
            List<QuestionGradingOptionDto> optionDtos = new ArrayList<>();
            List<Long> selectedIds = new ArrayList<>();

            if (sa != null && sa.getSelectedOptions() != null) {
                selectedIds = sa.getSelectedOptions().stream()
                        .map(o -> o.getQuestionOption().getId())
                        .toList();
            }

            if (q.getOptions() != null) {
                for (QuestionOption opt : q.getOptions()) {
                    optionDtos.add(QuestionGradingOptionDto.builder()
                            .id(opt.getId())
                            .optionOrder(opt.getOptionOrder())
                            .optionText(opt.getOptionText())
                            .isCorrect(opt.getIsCorrect())
                            .isSelectedByStudent(selectedIds.contains(opt.getId()))
                            .build());
                }
            }

            questionDtos.add(QuestionGradingItemDto.builder()
                    .gradeDetailId(gd != null ? gd.getId() : null)
                    .examQuestionId(eq.getId())
                    .questionOrder(eq.getQuestionOrder())
                    .questionType(q.getQuestionType())
                    .questionText(q.getQuestionText())
                    .maximumMarks(eq.getMarks())
                    .awardedMarks(gd != null ? gd.getAwardedMarks() : null)
                    .feedback(gd != null ? gd.getFeedback() : null)
                    .isAutoGraded(gd != null ? gd.getIsAutoGraded() : false)
                    .studentAnswerText(sa != null ? sa.getAnswerText() : null)
                    .studentSelectedOptionIds(selectedIds)
                    .options(optionDtos)
                    .build());
        }

        return AttemptGradingDto.builder()
                .attemptId(attempt.getId())
                .resultId(result.getId())
                .examinationId(exam.getId())
                .examinationTitle(exam.getTitle())
                .studentId(attempt.getStudent().getId())
                .studentRegistrationNumber(attempt.getStudent().getRegistrationNumber())
                .studentName(attempt.getStudent().getUser().getFullName())
                .totalMarks(exam.getTotalMarks())
                .currentEarnedMarks(result.getEarnedMarks())
                .gradingStatus(result.getGradingStatus())
                .publicationStatus(result.getPublicationStatus())
                .gradedBy(result.getGradedBy() != null ? result.getGradedBy().getFullName() : null)
                .gradedAt(result.getGradedAt())
                .questions(questionDtos)
                .build();
    }

    /**
     * Save draft manual grades without completing the examination grading.
     */
    @Transactional
    public AttemptGradingDto saveDraftGrades(Long attemptId, SaveGradesRequest request, Long userId, boolean isAdmin, boolean isOfficer) {
        return processGrades(attemptId, request, userId, isAdmin, isOfficer, false);
    }

    /**
     * Complete manual grading. Validates all questions have non-negative awarded marks <= max marks.
     */
    @Transactional
    public AttemptGradingDto completeGrading(Long attemptId, SaveGradesRequest request, Long userId, boolean isAdmin, boolean isOfficer) {
        return processGrades(attemptId, request, userId, isAdmin, isOfficer, true);
    }

    private AttemptGradingDto processGrades(Long attemptId, SaveGradesRequest request, Long userId,
                                            boolean isAdmin, boolean isOfficer, boolean finalizeGrading) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Attempt", "id", attemptId));

        Examination exam = attempt.getExamination();
        verifyInstructorOrOfficerAccess(exam, userId, isAdmin, isOfficer);

        User grader = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        ExamResult result = resultRepository.findByExaminationIdAndStudentId(exam.getId(), attempt.getStudent().getId())
                .orElseGet(() -> autoGradeAttempt(attempt));

        if (result.getPublicationStatus() == PublicationStatus.PUBLISHED) {
            throw new BusinessRuleException("Cannot modify grades directly because the result has already been PUBLISHED. Use the authorized audit correction process.");
        }

        // Apply question marks from request
        if (request != null && request.getGrades() != null) {
            for (SaveGradeItemRequest item : request.getGrades()) {
                ExamQuestion eq = examQuestionRepository.findById(item.getExamQuestionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Exam Question", "id", item.getExamQuestionId()));

                if (!eq.getExamination().getId().equals(exam.getId())) {
                    throw new BusinessRuleException("Question ID " + item.getExamQuestionId() + " does not belong to this examination.");
                }

                GradeDetail gd = gradeDetailRepository.findByExamResultIdAndExamQuestionId(result.getId(), eq.getId())
                        .orElseGet(() -> GradeDetail.builder()
                                .examResult(result)
                                .examQuestion(eq)
                                .maximumMarks(eq.getMarks())
                                .build());

                // Validation rules
                if (item.getAwardedMarks() != null) {
                    if (item.getAwardedMarks().compareTo(BigDecimal.ZERO) < 0) {
                        throw new BusinessRuleException("Awarded mark cannot be negative for question order " + eq.getQuestionOrder());
                    }
                    if (eq.getMarks() != null && item.getAwardedMarks().compareTo(eq.getMarks()) > 0) {
                        throw new BusinessRuleException(String.format(
                                "Awarded marks (%.2f) exceeds maximum marks (%.2f) for question order %d",
                                item.getAwardedMarks(), eq.getMarks(), eq.getQuestionOrder()));
                    }
                    gd.setAwardedMarks(item.getAwardedMarks());
                } else if (!finalizeGrading) {
                    // Allow saving null during draft
                    gd.setAwardedMarks(null);
                }

                if (item.getFeedback() != null) {
                    gd.setFeedback(item.getFeedback().trim());
                }
                gd.setGradedBy(grader);
                gd.setGradedAt(Instant.now());
                gradeDetailRepository.save(gd);
            }
        }

        // Check if finalizing
        List<GradeDetail> allGrades = gradeDetailRepository.findByExamResultId(result.getId());
        List<ExamQuestion> allQuestions = examQuestionRepository.findByExaminationIdOrderByQuestionOrderAsc(exam.getId());

        if (finalizeGrading) {
            // Validate EVERY question has an awarded mark and maximum mark
            for (ExamQuestion eq : allQuestions) {
                GradeDetail gd = allGrades.stream()
                        .filter(g -> g.getExamQuestion().getId().equals(eq.getId()))
                        .findFirst()
                        .orElse(null);

                if (gd == null || gd.getAwardedMarks() == null) {
                    throw new BusinessRuleException("Cannot complete grading. Question order " + eq.getQuestionOrder() + " has not been awarded marks.");
                }
                if (eq.getMarks() == null) {
                    throw new BusinessRuleException("Cannot complete grading. Question order " + eq.getQuestionOrder() + " has no configured maximum marks.");
                }
            }

            BigDecimal earnedTotal = allGrades.stream()
                    .map(GradeDetail::getAwardedMarks)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            result.setEarnedMarks(earnedTotal);
            result.setGradingStatus(GradingStatus.COMPLETED);
            result.setGradedBy(grader);
            result.setGradedAt(Instant.now());

            attempt.setIsGraded(true);
            attempt.setTotalScore(earnedTotal);
            attemptRepository.save(attempt);
        } else {
            result.setGradingStatus(GradingStatus.IN_PROGRESS);
            result.setGradedBy(grader);
            result.setGradedAt(Instant.now());
        }

        resultRepository.save(result);
        return getAttemptGrading(attempt.getId(), userId, isAdmin, isOfficer);
    }

    /**
     * Publish an individual completed exam result (EXAM_OFFICER or ADMIN).
     */
    @Transactional
    public ExamResult publishResult(Long resultId, Long officerId) {
        ExamResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Result", "id", resultId));

        if (result.getGradingStatus() != GradingStatus.COMPLETED) {
            throw new BusinessRuleException("Cannot publish result. Grading status is: " + result.getGradingStatus() + ". Only COMPLETED results can be published.");
        }

        User officer = userRepository.findById(officerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", officerId));

        result.setPublicationStatus(PublicationStatus.PUBLISHED);
        result.setPublishedBy(officer);
        result.setPublishedAt(Instant.now());

        ExamResult saved = resultRepository.save(result);
        try {
            notificationService.sendNotificationToUser(
                    saved.getStudent().getUser(),
                    NotificationType.RESULTS_PUBLISHED,
                    "Examination Results Published: " + saved.getExamination().getTitle(),
                    "Your results have been officially published. Earned Marks: " +
                            (saved.getEarnedMarks() != null ? String.format("%.2f / %.2f", saved.getEarnedMarks(), saved.getTotalMarks()) : "Pending"),
                    saved.getAttempt() != null ? saved.getAttempt().getSchedule() : null
            );
        } catch (Exception e) {
            log.warn("Failed to send result publication notification: {}", e.getMessage());
        }

        return saved;
    }

    /**
     * Publish all completed exam results for an examination (EXAM_OFFICER or ADMIN).
     */
    @Transactional
    public int publishAllExamResults(Long examId, Long officerId) {
        Examination exam = examinationRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Examination", "id", examId));

        User officer = userRepository.findById(officerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", officerId));

        List<ExamResult> results = resultRepository.findByExaminationId(exam.getId());
        int count = 0;

        for (ExamResult r : results) {
            if (r.getGradingStatus() == GradingStatus.COMPLETED && r.getPublicationStatus() != PublicationStatus.PUBLISHED) {
                r.setPublicationStatus(PublicationStatus.PUBLISHED);
                r.setPublishedBy(officer);
                r.setPublishedAt(Instant.now());
                ExamResult saved = resultRepository.save(r);
                count++;
                try {
                    notificationService.sendNotificationToUser(
                            saved.getStudent().getUser(),
                            NotificationType.RESULTS_PUBLISHED,
                            "Examination Results Published: " + exam.getTitle(),
                            "Your results have been officially published. Earned Marks: " +
                                    (saved.getEarnedMarks() != null ? String.format("%.2f / %.2f", saved.getEarnedMarks(), saved.getTotalMarks()) : "Pending"),
                            saved.getAttempt() != null ? saved.getAttempt().getSchedule() : null
                    );
                } catch (Exception e) {
                    log.warn("Failed to send result publication notification: {}", e.getMessage());
                }
            }
        }

        return count;
    }

    /**
     * Correct an already published result with mandatory audit trail log.
     */
    @Transactional
    public ExamResult correctPublishedResult(Long resultId, ResultCorrectionRequest request, Long userId, boolean isAdmin, boolean isOfficer) {
        ExamResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Result", "id", resultId));

        verifyInstructorOrOfficerAccess(result.getExamination(), userId, isAdmin, isOfficer);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!StringUtils.hasText(request.getReason())) {
            throw new BusinessRuleException("Audit reason is required for correcting a published result.");
        }

        if (request.getNewEarnedMarks().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("New earned marks cannot be negative.");
        }

        if (request.getNewEarnedMarks().compareTo(result.getTotalMarks()) > 0) {
            throw new BusinessRuleException(String.format(
                    "New earned marks (%.2f) cannot exceed examination total marks (%.2f).",
                    request.getNewEarnedMarks(), result.getTotalMarks()));
        }

        // Record audit
        ResultCorrectionAudit audit = ResultCorrectionAudit.builder()
                .examResult(result)
                .correctedBy(user)
                .previousEarnedMarks(result.getEarnedMarks())
                .newEarnedMarks(request.getNewEarnedMarks())
                .reason(request.getReason().trim())
                .correctedAt(Instant.now())
                .build();
        correctionAuditRepository.save(audit);

        // Update result
        result.setEarnedMarks(request.getNewEarnedMarks());
        result.setGradedBy(user);
        result.setGradedAt(Instant.now());

        if (result.getAttempt() != null) {
            ExamAttempt attempt = result.getAttempt();
            attempt.setTotalScore(request.getNewEarnedMarks());
            attemptRepository.save(attempt);
        }

        return resultRepository.save(result);
    }

    /**
     * Student Results view: Grouped by course.
     * Shows earned marks / total marks ONLY when PUBLISHED.
     * Shows "Not Attempted" when unattempted. Shows "Pending" when under grading.
     * Strictly NO pass/fail calculations or labels!
     */
    @Transactional(readOnly = true)
    public List<StudentCourseResultsDto> getStudentCourseResults(Long userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new UnauthorizedException("Student profile not found for user ID: " + userId));

        // Find student enrollments
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(student.getId());
        List<StudentCourseResultsDto> courseResults = new ArrayList<>();

        for (Enrollment enr : enrollments) {
            CourseOffering co = enr.getCourseOffering();
            if (co == null || !Boolean.TRUE.equals(co.getIsActive())) continue;

            Course course = co.getCourse();
            List<Examination> exams = examinationRepository.findByCourseOfferingId(co.getId());

            List<StudentExamResultDto> examResultsList = new ArrayList<>();

            for (Examination exam : exams) {
                // Only consider examinations that were scheduled or completed
                if (exam.getStatus() == ExamStatus.DRAFT || exam.getStatus() == ExamStatus.REJECTED) {
                    continue;
                }

                // Check for result
                Optional<ExamResult> resultOpt = resultRepository.findByExaminationIdAndStudentId(exam.getId(), student.getId());
                // Check for attempt
                List<ExamAttempt> attempts = attemptRepository.findByStudentId(student.getId()).stream()
                        .filter(a -> a.getExamination().getId().equals(exam.getId()))
                        .toList();
                ExamAttempt latestAttempt = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);

                if (resultOpt.isPresent() && resultOpt.get().getPublicationStatus() == PublicationStatus.PUBLISHED) {
                    ExamResult r = resultOpt.get();
                    List<StudentGradeDetailDto> details = new ArrayList<>();

                    // Load question details for student view (safe projection without answer keys)
                    List<GradeDetail> gds = gradeDetailRepository.findByExamResultId(r.getId());
                    for (GradeDetail gd : gds) {
                        details.add(StudentGradeDetailDto.builder()
                                .questionOrder(gd.getExamQuestion().getQuestionOrder())
                                .questionText(gd.getExamQuestion().getQuestion().getQuestionText())
                                .awardedMarks(gd.getAwardedMarks())
                                .maximumMarks(gd.getMaximumMarks())
                                .feedback(gd.getFeedback())
                                .build());
                    }

                    examResultsList.add(StudentExamResultDto.builder()
                            .resultId(r.getId())
                            .examinationId(exam.getId())
                            .examinationTitle(exam.getTitle())
                            .attemptStatus(latestAttempt != null ? latestAttempt.getStatus().name() : "SUBMITTED")
                            .displayStatus("Published")
                            .earnedMarks(r.getEarnedMarks())
                            .totalMarks(r.getTotalMarks())
                            .marksDisplay(r.getEarnedMarks() != null ? String.format("%.2f / %.2f", r.getEarnedMarks(), r.getTotalMarks()) : "Pending")
                            .publishedAt(r.getPublishedAt())
                            .details(details)
                            .build());
                } else if (latestAttempt != null) {
                    // Attempt was made, but result is not published yet
                    examResultsList.add(StudentExamResultDto.builder()
                            .examinationId(exam.getId())
                            .examinationTitle(exam.getTitle())
                            .attemptStatus(latestAttempt.getStatus().name())
                            .displayStatus("Pending")
                            .earnedMarks(null)
                            .totalMarks(exam.getTotalMarks())
                            .marksDisplay("Pending")
                            .build());
                } else {
                    // No attempt made by student
                    examResultsList.add(StudentExamResultDto.builder()
                            .examinationId(exam.getId())
                            .examinationTitle(exam.getTitle())
                            .attemptStatus("NOT_ATTEMPTED")
                            .displayStatus("Not Attempted")
                            .earnedMarks(null)
                            .totalMarks(exam.getTotalMarks())
                            .marksDisplay("Not Attempted")
                            .build());
                }
            }

            courseResults.add(StudentCourseResultsDto.builder()
                    .courseId(course.getId())
                    .courseCode(course.getCode())
                    .courseName(course.getName())
                    .departmentName(course.getDepartment() != null ? course.getDepartment().getName() : "")
                    .semesterName(co.getSemester() != null ? co.getSemester().getName() : "")
                    .examinations(examResultsList)
                    .build());
        }

        return courseResults;
    }

    /**
     * Course Results Roster view for instructors and officers.
     * Shows all students registered or eligible for an examination, their attempt status,
     * earned marks, and publication status.
     */
    @Transactional(readOnly = true)
    public List<CourseExamRosterItemDto> getCourseExamRoster(Long examId, Long userId, boolean isAdmin, boolean isOfficer) {
        Examination exam = examinationRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Examination", "id", examId));

        verifyInstructorOrOfficerAccess(exam, userId, isAdmin, isOfficer);

        CourseOffering co = exam.getCourseOffering();
        String courseTitle = co.getCourse().getCode() + " - " + co.getCourse().getName();

        // 1. Get all students eligible from enrollment
        List<Enrollment> enrollments = enrollmentRepository.findByCourseOfferingId(co.getId());
        Set<Student> students = enrollments.stream()
                .map(Enrollment::getStudent)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 2. Fetch all attempts and results for this examination
        List<ExamAttempt> attempts = attemptRepository.findAll().stream()
                .filter(a -> a.getExamination().getId().equals(exam.getId()))
                .toList();
        Map<Long, ExamAttempt> attemptMap = attempts.stream()
                .collect(Collectors.toMap(a -> a.getStudent().getId(), a -> a, (a1, a2) -> a1));

        List<ExamResult> results = resultRepository.findByExaminationId(exam.getId());
        Map<Long, ExamResult> resultMap = results.stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (r1, r2) -> r1));

        List<CourseExamRosterItemDto> roster = new ArrayList<>();

        for (Student student : students) {
            ExamAttempt attempt = attemptMap.get(student.getId());
            ExamResult result = resultMap.get(student.getId());

            String attemptStatus;
            String gradingStatus;
            String publicationStatus;
            BigDecimal earnedMarks = null;
            String marksDisplay;
            String gradedByName = null;
            Instant gradedAt = null;
            String publishedByName = null;
            Instant publishedAt = null;

            if (result != null) {
                attemptStatus = attempt != null ? formatAttemptStatus(attempt.getStatus()) : "Submitted";
                gradingStatus = result.getGradingStatus() == GradingStatus.COMPLETED ? "Grading Completed" : "Pending";
                publicationStatus = result.getPublicationStatus() == PublicationStatus.PUBLISHED ? "Published" : "Draft";
                earnedMarks = result.getEarnedMarks();
                marksDisplay = earnedMarks != null ? String.format("%.2f / %.2f", earnedMarks, exam.getTotalMarks()) : "— / " + exam.getTotalMarks();
                gradedByName = result.getGradedBy() != null ? result.getGradedBy().getFullName() : null;
                gradedAt = result.getGradedAt();
                publishedByName = result.getPublishedBy() != null ? result.getPublishedBy().getFullName() : null;
                publishedAt = result.getPublishedAt();
            } else if (attempt != null) {
                attemptStatus = formatAttemptStatus(attempt.getStatus());
                gradingStatus = "Pending";
                publicationStatus = "Draft";
                marksDisplay = "— / " + exam.getTotalMarks();
            } else {
                attemptStatus = "Not Attempted";
                gradingStatus = "—";
                publicationStatus = "—";
                marksDisplay = "Not Attempted";
            }

            roster.add(CourseExamRosterItemDto.builder()
                    .studentId(student.getId())
                    .registrationNumber(student.getRegistrationNumber())
                    .studentName(student.getUser().getFullName())
                    .courseTitle(courseTitle)
                    .examinationTitle(exam.getTitle())
                    .attemptId(attempt != null ? attempt.getId() : null)
                    .resultId(result != null ? result.getId() : null)
                    .attemptStatus(attemptStatus)
                    .gradingStatus(gradingStatus)
                    .publicationStatus(publicationStatus)
                    .earnedMarks(earnedMarks)
                    .totalMarks(exam.getTotalMarks())
                    .marksDisplay(marksDisplay)
                    .gradedBy(gradedByName)
                    .gradedAt(gradedAt)
                    .publishedBy(publishedByName)
                    .publishedAt(publishedAt)
                    .build());
        }

        roster.sort(Comparator.comparing(CourseExamRosterItemDto::getRegistrationNumber));
        return roster;
    }

    /**
     * CSV export generator for course examination results.
     */
    @Transactional(readOnly = true)
    public String generateResultsCsv(Long examId, Long userId, boolean isAdmin, boolean isOfficer) {
        List<CourseExamRosterItemDto> roster = getCourseExamRoster(examId, userId, isAdmin, isOfficer);
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("Student ID,Student Name,Course,Examination,Attempt Status,Grading Status,Publication Status,Earned Marks,Total Marks\n");

        for (CourseExamRosterItemDto item : roster) {
            sb.append(escapeCsv(item.getRegistrationNumber())).append(",");
            sb.append(escapeCsv(item.getStudentName())).append(",");
            sb.append(escapeCsv(item.getCourseTitle())).append(",");
            sb.append(escapeCsv(item.getExaminationTitle())).append(",");
            sb.append(escapeCsv(item.getAttemptStatus())).append(",");
            sb.append(escapeCsv(item.getGradingStatus())).append(",");
            sb.append(escapeCsv(item.getPublicationStatus())).append(",");
            sb.append(item.getEarnedMarks() != null ? item.getEarnedMarks().toString() : "—").append(",");
            sb.append(item.getTotalMarks() != null ? item.getTotalMarks().toString() : "").append("\n");
        }

        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String formatAttemptStatus(AttemptStatus status) {
        if (status == null) return "Not Attempted";
        return switch (status) {
            case IN_PROGRESS -> "In Progress";
            case SUBMITTED -> "Submitted";
            case EXPIRED -> "Expired";
            case ABANDONED -> "Abandoned";
        };
    }

    private void verifyInstructorOrOfficerAccess(Examination exam, Long userId, boolean isAdmin, boolean isOfficer) {
        if (isAdmin || isOfficer) return;

        // Verify that instructor is assigned to this course offering or created this exam
        CourseOffering co = exam.getCourseOffering();
        boolean isCreator = exam.getCreatedBy() != null && exam.getCreatedBy().getId().equals(userId);
        boolean isAssignedInstructor = co != null && co.getInstructor() != null
                && co.getInstructor().getUser().getId().equals(userId);

        if (!isCreator && !isAssignedInstructor) {
            throw new UnauthorizedException("You are not authorized to access results for this examination.");
        }
    }

    private Long getCorrectOptionIdFromQuestion(Question question) {
        if (question.getOptions() == null) return null;
        return question.getOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .map(QuestionOption::getId)
                .findFirst()
                .orElse(null);
    }

    private Set<Long> getCorrectOptionIdsFromQuestion(Question question) {
        if (question.getOptions() == null) return Collections.emptySet();
        return question.getOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .map(QuestionOption::getId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public InstructorDashboardStatsDto getInstructorDashboardStats(Long userId) {
        Instructor instructor = instructorRepository.findByUserId(userId).orElse(null);
        if (instructor == null) {
            instructorService.syncInstructorsFromUsers();
            instructor = instructorRepository.findByUserId(userId).orElse(null);
        }

        List<CourseOffering> offerings = instructor != null
                ? courseOfferingRepository.findByInstructorId(instructor.getId())
                : List.of();

        long assignedCoursesCount = offerings.size();
        long totalEnrolledStudents = offerings.stream()
                .mapToLong(o -> enrollmentRepository.countByCourseOfferingIdAndStatus(o.getId(), EnrollmentStatus.ACTIVE))
                .sum();

        long totalQuestionsCount = questionRepository.countByCreatedById(userId);

        List<Examination> exams = examinationRepository.findByCreatedById(userId);
        long totalExamsCount = exams.size();
        long draftExamsCount = exams.stream().filter(e -> e.getStatus() == ExamStatus.DRAFT).count();
        long pendingReviewExamsCount = exams.stream().filter(e -> e.getStatus() == ExamStatus.SUBMITTED).count();
        long approvedExamsCount = exams.stream()
                .filter(e -> e.getStatus() == ExamStatus.APPROVED || e.getStatus() == ExamStatus.SCHEDULED)
                .count();

        long pendingGradingSubmissionsCount = 0;
        long completedGradingCount = 0;

        for (Examination exam : exams) {
            List<ExamAttempt> attempts = attemptRepository.findByExaminationId(exam.getId());
            for (ExamAttempt att : attempts) {
                if (att.getStatus() == AttemptStatus.SUBMITTED) {
                    Optional<ExamResult> resOpt = resultRepository.findByAttemptId(att.getId());
                    if (resOpt.isPresent()) {
                        ExamResult res = resOpt.get();
                        if (res.getEarnedMarks() == null || res.getPublicationStatus() != PublicationStatus.PUBLISHED) {
                            pendingGradingSubmissionsCount++;
                        } else {
                            completedGradingCount++;
                        }
                    } else {
                        pendingGradingSubmissionsCount++;
                    }
                }
            }
        }

        List<ExaminationDto> recentExams = exams.stream()
                .sorted((a, b) -> {
                    Instant tA = a.getCreatedAt() != null ? a.getCreatedAt() : Instant.EPOCH;
                    Instant tB = b.getCreatedAt() != null ? b.getCreatedAt() : Instant.EPOCH;
                    return tB.compareTo(tA);
                })
                .limit(5)
                .map(ExaminationDto::fromEntity)
                .toList();

        return InstructorDashboardStatsDto.builder()
                .assignedCoursesCount(assignedCoursesCount)
                .totalEnrolledStudents(totalEnrolledStudents)
                .totalQuestionsCount(totalQuestionsCount)
                .totalExamsCount(totalExamsCount)
                .draftExamsCount(draftExamsCount)
                .pendingReviewExamsCount(pendingReviewExamsCount)
                .approvedExamsCount(approvedExamsCount)
                .pendingGradingSubmissionsCount(pendingGradingSubmissionsCount)
                .completedGradingCount(completedGradingCount)
                .recentExams(recentExams)
                .build();
    }
}
