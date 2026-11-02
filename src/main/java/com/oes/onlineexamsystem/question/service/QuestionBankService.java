package com.oes.onlineexamsystem.question.service;

import com.oes.onlineexamsystem.academic.entity.Course;
import com.oes.onlineexamsystem.academic.repository.CourseRepository;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.common.exception.UnauthorizedException;
import com.oes.onlineexamsystem.question.dto.QuestionBankDto;
import com.oes.onlineexamsystem.question.entity.QuestionBank;
import com.oes.onlineexamsystem.question.repository.QuestionBankRepository;
import com.oes.onlineexamsystem.question.repository.QuestionRepository;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionBankService {

    private final QuestionBankRepository bankRepository;
    private final QuestionRepository questionRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public QuestionBankService(QuestionBankRepository bankRepository,
                               QuestionRepository questionRepository,
                               CourseRepository courseRepository,
                               UserRepository userRepository) {
        this.bankRepository = bankRepository;
        this.questionRepository = questionRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<QuestionBankDto> getAllBanks() {
        return bankRepository.findAll().stream()
                .map(this::mapBankWithCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionBankDto> getBanksByCourse(Long courseId) {
        return bankRepository.findByCourseId(courseId).stream()
                .map(this::mapBankWithCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionBankDto> getBanksByInstructor(Long userId) {
        return bankRepository.findByCreatedById(userId).stream()
                .map(this::mapBankWithCount)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuestionBankDto getBankById(Long id) {
        QuestionBank bank = bankRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionBank", "id", id));
        return mapBankWithCount(bank);
    }

    @Transactional
    public QuestionBankDto createBank(QuestionBankDto dto, Long userId) {
        Course course = courseRepository.findById(dto.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", dto.getCourseId()));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        QuestionBank bank = QuestionBank.builder()
                .course(course)
                .createdBy(user)
                .name(dto.getName().trim())
                .description(dto.getDescription())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return mapBankWithCount(bankRepository.save(bank));
    }

    @Transactional
    public void deactivateBank(Long id, Long userId, boolean isAdmin) {
        QuestionBank bank = bankRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionBank", "id", id));

        if (!isAdmin && !bank.getCreatedBy().getId().equals(userId)) {
            throw new UnauthorizedException("You are not authorized to modify this question bank");
        }

        bank.setIsActive(false);
        bankRepository.save(bank);
    }

    private QuestionBankDto mapBankWithCount(QuestionBank bank) {
        QuestionBankDto dto = QuestionBankDto.fromEntity(bank);
        dto.setQuestionCount(questionRepository.countByBankId(bank.getId()));
        return dto;
    }
}
