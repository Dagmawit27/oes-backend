package com.oes.onlineexamsystem.execution.service;

import com.oes.onlineexamsystem.academic.entity.Student;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.execution.entity.AttemptPasscodeAttempt;
import com.oes.onlineexamsystem.execution.entity.ExamAttempt;
import com.oes.onlineexamsystem.execution.entity.ExamEventType;
import com.oes.onlineexamsystem.execution.repository.AttemptPasscodeAttemptRepository;
import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class PasscodeSecurityService {

    private final PasswordEncoder passwordEncoder;
    private final AttemptPasscodeAttemptRepository passcodeAttemptRepository;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int RATE_LIMIT_MINUTES = 5;

    public PasscodeSecurityService(PasswordEncoder passwordEncoder,
                                   AttemptPasscodeAttemptRepository passcodeAttemptRepository) {
        this.passwordEncoder = passwordEncoder;
        this.passcodeAttemptRepository = passcodeAttemptRepository;
    }

    /**
     * Validate that passcodes are exactly 4 numeric digits and pairwise distinct.
     */
    public void validatePasscodeConfig(String p1, String p2, String p3) {
        if (p1 == null || !p1.matches("^\\d{4}$")) {
            throw new BusinessRuleException("Passcode 1 must be exactly four numeric digits (leading zeros preserved).");
        }
        if (p2 == null || !p2.matches("^\\d{4}$")) {
            throw new BusinessRuleException("Passcode 2 must be exactly four numeric digits (leading zeros preserved).");
        }
        if (p3 == null || !p3.matches("^\\d{4}$")) {
            throw new BusinessRuleException("Passcode 3 must be exactly four numeric digits (leading zeros preserved).");
        }
        if (p1.equals(p2) || p1.equals(p3) || p2.equals(p3)) {
            throw new BusinessRuleException("All three examination passcodes must be distinct.");
        }
    }

    public String hashPasscode(String rawPasscode) {
        return passwordEncoder.encode(rawPasscode);
    }

    /**
     * Rate-limiting check before verifying passcode.
     */
    @Transactional(readOnly = true)
    public void checkRateLimit(ExamSchedule schedule, Student student) {
        Instant cutoff = Instant.now().minus(RATE_LIMIT_MINUTES, ChronoUnit.MINUTES);
        long recentFailures = passcodeAttemptRepository.countRecentFailedAttempts(schedule.getId(), student.getId(), cutoff);
        if (recentFailures >= MAX_FAILED_ATTEMPTS) {
            log.warn("Rate limit exceeded for student {} on schedule {}", student.getId(), schedule.getId());
            throw new BusinessRuleException("Too many incorrect passcode attempts. Access locked for 5 minutes. Please wait and try again.");
        }
    }

    /**
     * Authoritative Passcode 1 verification for Initial Exam Start.
     */
    @Transactional
    public void verifyInitialStartPasscode(ExamSchedule schedule, Student student, String rawPasscode, String ipAddress) {
        checkRateLimit(schedule, student);

        if (schedule.getPasscodeHash1() == null) {
            // Backward compatibility fallback if legacy schedule lacked passcodes
            return;
        }

        if (rawPasscode == null || rawPasscode.trim().isEmpty()) {
            recordAttempt(schedule, student, "START", false, ipAddress);
            throw new BusinessRuleException("Passcode 1 is required to start this examination.");
        }

        boolean matches = passwordEncoder.matches(rawPasscode.trim(), schedule.getPasscodeHash1());
        recordAttempt(schedule, student, "START", matches, ipAddress);

        if (!matches) {
            log.warn("Invalid Passcode 1 entered by student {} for schedule {}", student.getId(), schedule.getId());
            throw new BusinessRuleException("Invalid Passcode 1. Please verify the code with your examination proctor.");
        }
    }

    /**
     * Authoritative Passcode 2 or 3 verification for Resuming a Paused Attempt.
     */
    @Transactional
    public void verifyResumePasscode(ExamAttempt attempt, String rawPasscode, String ipAddress) {
        ExamSchedule schedule = attempt.getSchedule();
        Student student = attempt.getStudent();

        checkRateLimit(schedule, student);

        int resumeStage = attempt.getResumeStage();
        String expectedHash;
        String requiredPasscodeLabel;
        String attemptType;

        if (resumeStage <= 1) {
            expectedHash = schedule.getPasscodeHash2();
            requiredPasscodeLabel = "Passcode 2 (First Resume)";
            attemptType = "RESUME_1";
        } else {
            expectedHash = schedule.getPasscodeHash3();
            requiredPasscodeLabel = "Passcode 3 (Subsequent Resumes)";
            attemptType = "RESUME_2_PLUS";
        }

        if (expectedHash == null) {
            return;
        }

        if (rawPasscode == null || rawPasscode.trim().isEmpty()) {
            recordAttempt(schedule, student, attemptType, false, ipAddress);
            throw new BusinessRuleException(requiredPasscodeLabel + " is required to resume this examination.");
        }

        // Specifically reject Passcode 1 on resume
        if (schedule.getPasscodeHash1() != null && passwordEncoder.matches(rawPasscode.trim(), schedule.getPasscodeHash1())) {
            recordAttempt(schedule, student, attemptType, false, ipAddress);
            throw new BusinessRuleException("Passcode 1 cannot be reused to resume an examination. Please enter " + requiredPasscodeLabel + ".");
        }

        // Specifically reject Passcode 2 after stage 1
        if (resumeStage > 1 && schedule.getPasscodeHash2() != null && passwordEncoder.matches(rawPasscode.trim(), schedule.getPasscodeHash2())) {
            recordAttempt(schedule, student, attemptType, false, ipAddress);
            throw new BusinessRuleException("Passcode 2 is only valid for the first resumption. For stage " + resumeStage + ", enter Passcode 3.");
        }

        boolean matches = passwordEncoder.matches(rawPasscode.trim(), expectedHash);
        recordAttempt(schedule, student, attemptType, matches, ipAddress);

        if (!matches) {
            log.warn("Invalid {} entered by student {} for attempt {}", requiredPasscodeLabel, student.getId(), attempt.getId());
            throw new BusinessRuleException("Invalid " + requiredPasscodeLabel + ". Please consult your examination proctor.");
        }
    }

    private void recordAttempt(ExamSchedule schedule, Student student, String attemptType, boolean isSuccessful, String ipAddress) {
        AttemptPasscodeAttempt record = AttemptPasscodeAttempt.builder()
                .schedule(schedule)
                .student(student)
                .attemptType(attemptType)
                .isSuccessful(isSuccessful)
                .ipAddress(ipAddress)
                .build();
        passcodeAttemptRepository.save(record);
    }
}
