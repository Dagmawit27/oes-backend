package com.oes.onlineexamsystem.examination.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.academic.entity.CourseOffering;
import com.oes.onlineexamsystem.academic.repository.CourseOfferingRepository;
import com.oes.onlineexamsystem.academic.repository.InstructorRepository;
import com.oes.onlineexamsystem.common.dto.PagedResponse;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.common.exception.UnauthorizedException;
import com.oes.onlineexamsystem.examination.dto.*;
import com.oes.onlineexamsystem.examination.entity.ApprovalDecision;
import com.oes.onlineexamsystem.examination.entity.ExamApproval;
import com.oes.onlineexamsystem.examination.entity.ExamQuestion;
import com.oes.onlineexamsystem.examination.entity.ExamStatus;
import com.oes.onlineexamsystem.examination.entity.Examination;
import com.oes.onlineexamsystem.examination.repository.ExamApprovalRepository;
import com.oes.onlineexamsystem.examination.repository.ExamQuestionRepository;
import com.oes.onlineexamsystem.examination.repository.ExaminationRepository;
import com.oes.onlineexamsystem.question.entity.Question;
import com.oes.onlineexamsystem.question.entity.QuestionOption;
import com.oes.onlineexamsystem.question.repository.QuestionRepository;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ExaminationService {

    private final ExaminationRepository examinationRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamApprovalRepository examApprovalRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final InstructorRepository instructorRepository;
    private final ObjectMapper objectMapper;

    public ExaminationService(ExaminationRepository examinationRepository,
                              ExamQuestionRepository examQuestionRepository,
                              ExamApprovalRepository examApprovalRepository,
                              CourseOfferingRepository courseOfferingRepository,
                              QuestionRepository questionRepository,
                              UserRepository userRepository,
                              InstructorRepository instructorRepository,
                              ObjectMapper objectMapper) {
        this.examinationRepository = examinationRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.examApprovalRepository = examApprovalRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.instructorRepository = instructorRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ExaminationDto> getExaminations(Long courseOfferingId,
                                                         ExamStatus status,
                                                         Long createdById,
                                                         String search,
                                                         Pageable pageable) {
        Specification<Examination> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (courseOfferingId != null) {
                predicates.add(cb.equal(root.get("courseOffering").get("id"), courseOfferingId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (createdById != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), createdById));
            }
            if (StringUtils.hasText(search)) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), searchPattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), searchPattern);

                Join<Examination, CourseOffering> offeringJoin = root.join("courseOffering", JoinType.LEFT);
                Predicate courseMatch = cb.like(cb.lower(offeringJoin.get("course").get("name")), searchPattern);
                Predicate codeMatch = cb.like(cb.lower(offeringJoin.get("course").get("code")), searchPattern);

                predicates.add(cb.or(titleMatch, descMatch, courseMatch, codeMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Examination> page = examinationRepository.findAll(spec, pageable);
        List<ExaminationDto> dtos = page.getContent().stream()
                .map(e -> ExaminationDto.fromEntity(e, false))
                .toList();

        return PagedResponse.<ExaminationDto>builder()
                .content(dtos)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public ExaminationDto getExaminationById(Long id) {
        Examination exam = examinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examination", "id", id));
        return ExaminationDto.fromEntity(exam, true);
    }

    @Transactional
    public ExaminationDto createExamination(CreateExaminationRequest request, Long userId) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        CourseOffering offering = courseOfferingRepository.findById(request.getCourseOfferingId())
                .orElseThrow(() -> new ResourceNotFoundException("Course Assignment", "id", request.getCourseOfferingId()));

        boolean isAdmin = creator.getRoles().stream()
                .anyMatch(r -> r.getName().name().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            // Verify assigned instructor ownership
            if (offering.getInstructor() == null || offering.getInstructor().getUser() == null ||
                    !offering.getInstructor().getUser().getId().equals(userId)) {
                throw new UnauthorizedException("You are only permitted to create examinations for course assignments assigned to you.");
            }
        }

        if (examinationRepository.existsByCourseOfferingIdAndTitleIgnoreCase(offering.getId(), request.getTitle().trim())) {
            throw new BusinessRuleException("An examination with title '" + request.getTitle().trim() + "' already exists for this course assignment.");
        }

        validateQuestionMarks(request.getQuestions(), request.getTotalMarks(), request.getPassingMarks());

        Examination examination = Examination.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .instructions(request.getInstructions())
                .courseOffering(offering)
                .createdBy(creator)
                .durationMinutes(request.getDurationMinutes())
                .totalMarks(request.getTotalMarks())
                .passingMarks(request.getPassingMarks())
                .randomizeQuestions(Boolean.TRUE.equals(request.getRandomizeQuestions()))
                .randomizeOptions(Boolean.TRUE.equals(request.getRandomizeOptions()))
                .attemptLimit(request.getAttemptLimit() != null ? request.getAttemptLimit() : 1)
                .negativeMarkingEnabled(Boolean.TRUE.equals(request.getNegativeMarkingEnabled()))
                .negativeMarkValue(request.getNegativeMarkValue() != null ? request.getNegativeMarkValue() : BigDecimal.ZERO)
                .status(ExamStatus.DRAFT)
                .version(1)
                .build();

        attachQuestions(examination, request.getQuestions());

        Examination saved = examinationRepository.save(examination);
        return ExaminationDto.fromEntity(saved, true);
    }

    @Transactional
    public ExaminationDto updateExamination(Long id, UpdateExaminationRequest request, Long userId, boolean isAdmin) {
        Examination exam = examinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examination", "id", id));

        if (!isAdmin && !exam.getCreatedBy().getId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to modify this examination.");
        }

        if (exam.getStatus() != ExamStatus.DRAFT && exam.getStatus() != ExamStatus.REJECTED) {
            throw new BusinessRuleException("Cannot modify examination in " + exam.getStatus() +
                    " status. Approved examinations are locked, and submitted examinations must be reviewed.");
        }

        if (examinationRepository.existsByCourseOfferingIdAndTitleIgnoreCaseAndIdNot(
                exam.getCourseOffering().getId(), request.getTitle().trim(), id)) {
            throw new BusinessRuleException("An examination with title '" + request.getTitle().trim() + "' already exists for this course assignment.");
        }

        validateQuestionMarks(request.getQuestions(), request.getTotalMarks(), request.getPassingMarks());

        exam.setTitle(request.getTitle().trim());
        exam.setDescription(request.getDescription());
        exam.setInstructions(request.getInstructions());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setTotalMarks(request.getTotalMarks());
        exam.setPassingMarks(request.getPassingMarks());
        exam.setRandomizeQuestions(Boolean.TRUE.equals(request.getRandomizeQuestions()));
        exam.setRandomizeOptions(Boolean.TRUE.equals(request.getRandomizeOptions()));
        exam.setAttemptLimit(request.getAttemptLimit() != null ? request.getAttemptLimit() : 1);
        exam.setNegativeMarkingEnabled(Boolean.TRUE.equals(request.getNegativeMarkingEnabled()));
        exam.setNegativeMarkValue(request.getNegativeMarkValue() != null ? request.getNegativeMarkValue() : BigDecimal.ZERO);

        // Update exam questions by matching existing ones to avoid unique constraint violations
        Map<Long, ExamQuestion> existingMap = new HashMap<>();
        for (ExamQuestion eq : exam.getExamQuestions()) {
            if (eq.getQuestion() != null) {
                existingMap.put(eq.getQuestion().getId(), eq);
            }
        }

        List<ExamQuestion> updatedQuestions = new ArrayList<>();
        int order = 1;
        for (SelectedQuestionRequest sq : request.getQuestions()) {
            int qOrder = sq.getQuestionOrder() != null ? sq.getQuestionOrder() : order;
            ExamQuestion eq = existingMap.remove(sq.getQuestionId());
            if (eq != null) {
                eq.setMarks(sq.getMarks());
                eq.setQuestionOrder(qOrder);
                updatedQuestions.add(eq);
            } else {
                Question question = questionRepository.findById(sq.getQuestionId())
                        .orElseThrow(() -> new ResourceNotFoundException("Question", "id", sq.getQuestionId()));
                if (!Boolean.TRUE.equals(question.getIsActive())) {
                    throw new BusinessRuleException("Question '" + question.getQuestionText() + "' is inactive and cannot be selected.");
                }
                ExamQuestion newEq = ExamQuestion.builder()
                        .examination(exam)
                        .question(question)
                        .questionOrder(qOrder)
                        .marks(sq.getMarks())
                        .questionVersion(question.getVersion())
                        .build();
                updatedQuestions.add(newEq);
            }
            order++;
        }

        exam.getExamQuestions().clear();
        exam.getExamQuestions().addAll(updatedQuestions);

        Examination updated = examinationRepository.save(exam);
        return ExaminationDto.fromEntity(updated, true);
    }

    @Transactional
    public ExaminationDto submitForApproval(Long id, Long userId, boolean isAdmin) {
        Examination exam = examinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examination", "id", id));

        if (!isAdmin && !exam.getCreatedBy().getId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to submit this examination.");
        }

        if (exam.getStatus() != ExamStatus.DRAFT && exam.getStatus() != ExamStatus.REJECTED) {
            throw new BusinessRuleException("Only DRAFT or REJECTED examinations can be submitted for approval. Current status: " + exam.getStatus());
        }

        if (exam.getExamQuestions().isEmpty()) {
            throw new BusinessRuleException("Cannot submit an examination without any questions.");
        }

        exam.setStatus(ExamStatus.SUBMITTED);
        Examination submitted = examinationRepository.save(exam);
        return ExaminationDto.fromEntity(submitted, true);
    }

    @Transactional
    public ExaminationDto reviewExamination(Long id, ReviewExaminationRequest request, Long reviewerId) {
        Examination exam = examinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examination", "id", id));

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", reviewerId));

        if (exam.getStatus() != ExamStatus.SUBMITTED) {
            throw new BusinessRuleException("Only examinations with SUBMITTED status can be reviewed. Current status: " + exam.getStatus());
        }

        if (request.getDecision() == ApprovalDecision.REJECTED) {
            if (!StringUtils.hasText(request.getComments())) {
                throw new BusinessRuleException("Mandatory reviewer feedback comments are required when rejecting an examination.");
            }
            exam.setStatus(ExamStatus.REJECTED);
        } else if (request.getDecision() == ApprovalDecision.APPROVED) {
            exam.setStatus(ExamStatus.APPROVED);

            // Generate immutable question snapshots for all linked questions
            for (ExamQuestion eq : exam.getExamQuestions()) {
                String snapshotJson = buildQuestionSnapshot(eq.getQuestion(), eq.getMarks());
                eq.setQuestionSnapshot(snapshotJson);
                eq.setQuestionVersion(eq.getQuestion().getVersion());
            }
        }

        // Audit review history
        ExamApproval approval = ExamApproval.builder()
                .examination(exam)
                .reviewer(reviewer)
                .decision(request.getDecision())
                .comments(request.getComments() != null ? request.getComments().trim() : "")
                .build();

        exam.getApprovals().add(approval);
        examApprovalRepository.save(approval);

        Examination reviewed = examinationRepository.save(exam);
        return ExaminationDto.fromEntity(reviewed, true);
    }

    @Transactional(readOnly = true)
    public List<ExamApprovalDto> getExaminationApprovals(Long examId) {
        if (!examinationRepository.existsById(examId)) {
            throw new ResourceNotFoundException("Examination", "id", examId);
        }
        return examApprovalRepository.findByExaminationIdOrderByReviewedAtDesc(examId).stream()
                .map(ExamApprovalDto::fromEntity)
                .toList();
    }

    @Transactional
    public void deleteExamination(Long id, Long userId, boolean isAdmin) {
        Examination exam = examinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Examination", "id", id));

        if (!isAdmin && !exam.getCreatedBy().getId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to delete this examination.");
        }

        if (exam.getStatus() == ExamStatus.APPROVED || exam.getStatus() == ExamStatus.SCHEDULED || exam.getStatus() == ExamStatus.ONGOING) {
            throw new BusinessRuleException("Cannot delete an examination in " + exam.getStatus() + " status.");
        }

        examinationRepository.delete(exam);
    }

    private void validateQuestionMarks(List<SelectedQuestionRequest> questions, BigDecimal totalMarks, BigDecimal passingMarks) {
        if (questions == null || questions.isEmpty()) {
            throw new BusinessRuleException("At least one question must be selected for the examination.");
        }

        BigDecimal sumOfMarks = questions.stream()
                .map(SelectedQuestionRequest::getMarks)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalMarks.compareTo(sumOfMarks) != 0) {
            throw new BusinessRuleException(String.format(
                    "Configured total marks (%.2f) does not match sum of selected question marks (%.2f).",
                    totalMarks, sumOfMarks));
        }

        if (passingMarks.compareTo(totalMarks) > 0) {
            throw new BusinessRuleException(String.format(
                    "Passing marks (%.2f) cannot exceed total examination marks (%.2f).",
                    passingMarks, totalMarks));
        }
    }

    private void attachQuestions(Examination exam, List<SelectedQuestionRequest> questionRequests) {
        int order = 1;
        for (SelectedQuestionRequest sq : questionRequests) {
            Question question = questionRepository.findById(sq.getQuestionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Question", "id", sq.getQuestionId()));

            if (!Boolean.TRUE.equals(question.getIsActive())) {
                throw new BusinessRuleException("Question '" + question.getQuestionText() + "' is inactive and cannot be selected.");
            }

            int questionOrder = sq.getQuestionOrder() != null ? sq.getQuestionOrder() : order;

            ExamQuestion eq = ExamQuestion.builder()
                    .examination(exam)
                    .question(question)
                    .questionOrder(questionOrder)
                    .marks(sq.getMarks())
                    .questionVersion(question.getVersion())
                    .build();

            exam.getExamQuestions().add(eq);
            order++;
        }
    }

    private String buildQuestionSnapshot(Question question, BigDecimal assignedMarks) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", question.getId());
            snapshot.put("questionText", question.getQuestionText());
            snapshot.put("questionType", question.getQuestionType().name());
            snapshot.put("assignedMarks", assignedMarks);
            snapshot.put("defaultMarks", question.getDefaultMarks());
            snapshot.put("explanation", question.getExplanation());
            snapshot.put("version", question.getVersion());

            List<Map<String, Object>> optionsList = new ArrayList<>();
            if (question.getOptions() != null) {
                for (QuestionOption opt : question.getOptions()) {
                    Map<String, Object> optMap = new LinkedHashMap<>();
                    optMap.put("id", opt.getId());
                    optMap.put("optionText", opt.getOptionText());
                    optMap.put("optionOrder", opt.getOptionOrder());
                    optMap.put("isCorrect", opt.getIsCorrect());
                    optionsList.add(optMap);
                }
            }
            snapshot.put("options", optionsList);

            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("Failed to generate question snapshot: " + e.getMessage());
        }
    }
}
