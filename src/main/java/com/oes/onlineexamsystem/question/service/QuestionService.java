package com.oes.onlineexamsystem.question.service;

import com.oes.onlineexamsystem.academic.entity.Course;
import com.oes.onlineexamsystem.academic.repository.CourseRepository;
import com.oes.onlineexamsystem.common.dto.PagedResponse;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.common.exception.UnauthorizedException;
import com.oes.onlineexamsystem.question.dto.*;
import com.oes.onlineexamsystem.question.entity.*;
import com.oes.onlineexamsystem.question.repository.QuestionBankRepository;
import com.oes.onlineexamsystem.question.repository.QuestionCategoryRepository;
import com.oes.onlineexamsystem.question.repository.QuestionOptionRepository;
import com.oes.onlineexamsystem.question.repository.QuestionRepository;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository optionRepository;
    private final QuestionBankRepository bankRepository;
    private final QuestionCategoryRepository categoryRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public QuestionService(QuestionRepository questionRepository,
                           QuestionOptionRepository optionRepository,
                           QuestionBankRepository bankRepository,
                           QuestionCategoryRepository categoryRepository,
                           CourseRepository courseRepository,
                           UserRepository userRepository) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.bankRepository = bankRepository;
        this.categoryRepository = categoryRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PagedResponse<QuestionDto> getQuestions(Long courseId,
                                                   Long bankId,
                                                   Long categoryId,
                                                   QuestionType type,
                                                   DifficultyLevel difficulty,
                                                   String search,
                                                   Boolean isActive,
                                                   Pageable pageable) {
        return getQuestions(courseId, bankId, categoryId, type, difficulty, search, isActive, null, pageable);
    }

    @Transactional(readOnly = true)
    public PagedResponse<QuestionDto> getQuestions(Long courseId,
                                                   Long bankId,
                                                   Long categoryId,
                                                   QuestionType type,
                                                   DifficultyLevel difficulty,
                                                   String search,
                                                   Boolean isActive,
                                                   Long createdById,
                                                   Pageable pageable) {

        Specification<Question> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (courseId != null) {
                predicates.add(cb.equal(root.get("course").get("id"), courseId));
            }
            if (bankId != null) {
                predicates.add(cb.equal(root.get("bank").get("id"), bankId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("questionType"), type));
            }
            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficultyLevel"), difficulty));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }
            if (createdById != null) {
                predicates.add(cb.equal(root.get("createdBy").get("id"), createdById));
            }
            if (StringUtils.hasText(search)) {
                predicates.add(cb.like(cb.lower(root.get("questionText")), "%" + search.trim().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Question> page = questionRepository.findAll(spec, pageable);
        List<QuestionDto> dtos = page.getContent().stream()
                .map(QuestionDto::fromEntity)
                .toList();

        return PagedResponse.<QuestionDto>builder()
                .content(dtos)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public QuestionDto getQuestionById(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));
        return QuestionDto.fromEntity(question);
    }

    /**
     * CRITICAL SECURITY METHOD:
     * Never returns answer keys, correct option indicators, or teacher explanations to students.
     */
    @Transactional(readOnly = true)
    public StudentQuestionDto getStudentQuestionById(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));

        if (!Boolean.TRUE.equals(question.getIsActive())) {
            throw new BusinessRuleException("The requested question is not currently active");
        }

        return StudentQuestionDto.fromEntity(question);
    }

    @Transactional
    public QuestionDto createQuestion(CreateQuestionRequest request, Long userId) {
        validateQuestionOptions(request.getQuestionType(), request.getOptions());

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", request.getCourseId()));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        QuestionBank bank = null;
        if (request.getBankId() != null) {
            bank = bankRepository.findById(request.getBankId())
                    .orElseThrow(() -> new ResourceNotFoundException("QuestionBank", "id", request.getBankId()));
        }

        QuestionCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("QuestionCategory", "id", request.getCategoryId()));
        }

        Question question = Question.builder()
                .course(course)
                .bank(bank)
                .category(category)
                .createdBy(user)
                .questionText(request.getQuestionText().trim())
                .questionType(request.getQuestionType())
                .difficultyLevel(request.getDifficultyLevel() != null ? request.getDifficultyLevel() : DifficultyLevel.MEDIUM)
                .defaultMarks(request.getDefaultMarks())
                .explanation(request.getExplanation())
                .version(1)
                .isActive(true)
                .build();

        Question savedQuestion = questionRepository.save(question);

        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            int order = 1;
            for (QuestionOptionDto optDto : request.getOptions()) {
                QuestionOption opt = QuestionOption.builder()
                        .question(savedQuestion)
                        .optionText(optDto.getOptionText().trim())
                        .optionOrder(optDto.getOptionOrder() != null ? optDto.getOptionOrder() : order++)
                        .isCorrect(Boolean.TRUE.equals(optDto.getIsCorrect()))
                        .explanation(optDto.getExplanation())
                        .build();
                savedQuestion.getOptions().add(opt);
            }
            savedQuestion = questionRepository.save(savedQuestion);
        }

        return QuestionDto.fromEntity(savedQuestion);
    }

    @Transactional
    public QuestionDto updateQuestion(Long id, UpdateQuestionRequest request, Long userId, boolean isAdmin) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));

        // Ownership enforcement: only question author or administrator can edit
        if (!isAdmin && !question.getCreatedBy().getId().equals(userId)) {
            throw new UnauthorizedException("You do not have permission to modify this question");
        }

        validateQuestionOptions(request.getQuestionType(), request.getOptions());

        if (request.getBankId() != null) {
            QuestionBank bank = bankRepository.findById(request.getBankId())
                    .orElseThrow(() -> new ResourceNotFoundException("QuestionBank", "id", request.getBankId()));
            question.setBank(bank);
        } else {
            question.setBank(null);
        }

        if (request.getCategoryId() != null) {
            QuestionCategory category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("QuestionCategory", "id", request.getCategoryId()));
            question.setCategory(category);
        } else {
            question.setCategory(null);
        }

        question.setQuestionText(request.getQuestionText().trim());
        question.setQuestionType(request.getQuestionType());
        question.setDifficultyLevel(request.getDifficultyLevel());
        question.setDefaultMarks(request.getDefaultMarks());
        question.setExplanation(request.getExplanation());
        if (request.getIsActive() != null) {
            question.setIsActive(request.getIsActive());
        }

        // Increment version number on each update to maintain history/audit trail
        question.setVersion(question.getVersion() + 1);

        // Replace options
        question.getOptions().clear();
        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            int order = 1;
            for (QuestionOptionDto optDto : request.getOptions()) {
                QuestionOption opt = QuestionOption.builder()
                        .question(question)
                        .optionText(optDto.getOptionText().trim())
                        .optionOrder(optDto.getOptionOrder() != null ? optDto.getOptionOrder() : order++)
                        .isCorrect(Boolean.TRUE.equals(optDto.getIsCorrect()))
                        .explanation(optDto.getExplanation())
                        .build();
                question.getOptions().add(opt);
            }
        }

        return QuestionDto.fromEntity(questionRepository.save(question));
    }

    @Transactional
    public void deleteQuestion(Long id, Long userId, boolean isAdmin) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));

        // Ownership enforcement
        if (!isAdmin && !question.getCreatedBy().getId().equals(userId)) {
            throw new UnauthorizedException("You do not have permission to delete this question");
        }

        // Soft deletion to preserve exam integrity
        question.setIsActive(false);
        questionRepository.save(question);
    }

    @Transactional
    public QuestionDto duplicateQuestion(Long id, Long userId) {
        Question original = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question", "id", id));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Question duplicate = Question.builder()
                .course(original.getCourse())
                .bank(original.getBank())
                .category(original.getCategory())
                .createdBy(user)
                .questionText(original.getQuestionText() + " (Copy)")
                .questionType(original.getQuestionType())
                .difficultyLevel(original.getDifficultyLevel())
                .defaultMarks(original.getDefaultMarks())
                .explanation(original.getExplanation())
                .version(1)
                .isActive(true)
                .build();

        Question saved = questionRepository.save(duplicate);

        for (QuestionOption opt : original.getOptions()) {
            QuestionOption copiedOpt = QuestionOption.builder()
                    .question(saved)
                    .optionText(opt.getOptionText())
                    .optionOrder(opt.getOptionOrder())
                    .isCorrect(opt.getIsCorrect())
                    .explanation(opt.getExplanation())
                    .build();
            saved.getOptions().add(copiedOpt);
        }

        return QuestionDto.fromEntity(questionRepository.save(saved));
    }

    /**
     * Enforces strict domain validation per question type:
     * - SINGLE_CHOICE: at least 2 options, exactly 1 correct
     * - MULTIPLE_CHOICE: at least 2 options, at least 1 correct
     * - TRUE_FALSE: exactly 2 options, exactly 1 correct
     * - SHORT_ANSWER: options represent accepted answer strings (if provided)
     * - ESSAY: options not required (subjective manual grading)
     */
    private void validateQuestionOptions(QuestionType type, List<QuestionOptionDto> options) {
        if (type == QuestionType.ESSAY) {
            return;
        }

        if (options == null || options.isEmpty()) {
            if (type == QuestionType.SHORT_ANSWER) {
                return; // Short answer can rely on manual grading or accepted options
            }
            throw new BusinessRuleException("Questions of type " + type + " require answer options");
        }

        long correctCount = options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).count();

        switch (type) {
            case SINGLE_CHOICE -> {
                if (options.size() < 2) {
                    throw new BusinessRuleException("Single choice question must have at least 2 options");
                }
                if (correctCount != 1) {
                    throw new BusinessRuleException("Single choice question must have exactly one correct option (Found: " + correctCount + ")");
                }
            }
            case MULTIPLE_CHOICE -> {
                if (options.size() < 2) {
                    throw new BusinessRuleException("Multiple choice question must have at least 2 options");
                }
                if (correctCount < 1) {
                    throw new BusinessRuleException("Multiple choice question must have at least one correct option");
                }
            }
            case TRUE_FALSE -> {
                if (options.size() != 2) {
                    throw new BusinessRuleException("True/False question must contain exactly 2 options ('True' and 'False')");
                }
                if (correctCount != 1) {
                    throw new BusinessRuleException("True/False question must have exactly one correct option");
                }
            }
            case SHORT_ANSWER -> {
                if (!options.isEmpty() && correctCount < 1) {
                    throw new BusinessRuleException("When providing accepted short answer keys, at least one must be marked correct");
                }
            }
        }
    }
}
