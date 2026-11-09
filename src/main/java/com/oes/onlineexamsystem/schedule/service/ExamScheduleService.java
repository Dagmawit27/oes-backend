package com.oes.onlineexamsystem.schedule.service;

import com.oes.onlineexamsystem.academic.entity.CourseOffering;
import com.oes.onlineexamsystem.academic.entity.Enrollment;
import com.oes.onlineexamsystem.academic.entity.EnrollmentStatus;
import com.oes.onlineexamsystem.academic.entity.Student;
import com.oes.onlineexamsystem.academic.repository.EnrollmentRepository;
import com.oes.onlineexamsystem.academic.repository.StudentRepository;
import com.oes.onlineexamsystem.common.dto.PagedResponse;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.examination.entity.ExamStatus;
import com.oes.onlineexamsystem.examination.entity.Examination;
import com.oes.onlineexamsystem.examination.repository.ExaminationRepository;
import com.oes.onlineexamsystem.execution.entity.AttemptStatus;
import com.oes.onlineexamsystem.execution.entity.ExamAttempt;
import com.oes.onlineexamsystem.execution.repository.ExamAttemptRepository;
import com.oes.onlineexamsystem.execution.service.PasscodeSecurityService;
import com.oes.onlineexamsystem.notification.service.NotificationService;
import com.oes.onlineexamsystem.schedule.dto.*;
import com.oes.onlineexamsystem.schedule.entity.*;
import com.oes.onlineexamsystem.schedule.repository.ExamEligibilityRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamNotificationRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamRoomRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamScheduleRepository;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class ExamScheduleService {

    private final ExamScheduleRepository scheduleRepository;
    private final ExamEligibilityRepository eligibilityRepository;
    private final ExamRoomRepository roomRepository;
    private final ExamNotificationRepository notificationRepository;
    private final ExaminationRepository examinationRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PasscodeSecurityService passcodeSecurityService;
    private final ExamAttemptRepository attemptRepository;

    private static final List<ScheduleStatus> ACTIVE_STATUSES = List.of(
            ScheduleStatus.SCHEDULED, ScheduleStatus.ONGOING, ScheduleStatus.RESCHEDULED
    );

    public ExamScheduleService(ExamScheduleRepository scheduleRepository,
                               ExamEligibilityRepository eligibilityRepository,
                               ExamRoomRepository roomRepository,
                               ExamNotificationRepository notificationRepository,
                               ExaminationRepository examinationRepository,
                               EnrollmentRepository enrollmentRepository,
                               StudentRepository studentRepository,
                               UserRepository userRepository,
                               NotificationService notificationService,
                               PasscodeSecurityService passcodeSecurityService,
                               ExamAttemptRepository attemptRepository) {
        this.scheduleRepository = scheduleRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.roomRepository = roomRepository;
        this.notificationRepository = notificationRepository;
        this.examinationRepository = examinationRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.passcodeSecurityService = passcodeSecurityService;
        this.attemptRepository = attemptRepository;
    }

    public static Instant calculateOfficialAttemptDeadline(Instant attemptStart, int durationMinutes, Instant scheduledCloseAt) {
        Instant personalDeadline = attemptStart.plus(durationMinutes, ChronoUnit.MINUTES);
        return scheduledCloseAt != null && scheduledCloseAt.isBefore(personalDeadline) ? scheduledCloseAt : personalDeadline;
    }

    @Transactional(readOnly = true)
    public PagedResponse<ExamScheduleDto> getSchedules(Long courseOfferingId,
                                                       ScheduleStatus status,
                                                       Instant from,
                                                       Instant to,
                                                       Pageable pageable) {
        Specification<ExamSchedule> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (courseOfferingId != null) {
                predicates.add(cb.equal(root.get("examination").get("courseOffering").get("id"), courseOfferingId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null && to != null) {
                predicates.add(cb.between(root.get("scheduledStartAt"), from, to));
            } else if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledStartAt"), from));
            } else if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledStartAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ExamSchedule> page = scheduleRepository.findAll(spec, pageable);
        List<ExamScheduleDto> dtos = page.getContent().stream()
                .map(ExamScheduleDto::fromEntity)
                .toList();

        return PagedResponse.<ExamScheduleDto>builder()
                .content(dtos)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ExamScheduleDto> getCalendarSchedules(Instant start, Instant end) {
        return scheduleRepository.findByScheduledStartAtBetweenOrderByScheduledStartAtAsc(start, end).stream()
                .map(s -> ExamScheduleDto.fromEntity(s, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExamScheduleDto getScheduleById(Long id) {
        ExamSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Schedule", "id", id));
        return ExamScheduleDto.fromEntity(schedule, true);
    }

    @Transactional
    public ExamScheduleDto createSchedule(CreateScheduleRequest request, Long officerId) {
        User officer = userRepository.findById(officerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", officerId));

        Examination examination = examinationRepository.findById(request.getExaminationId())
                .orElseThrow(() -> new ResourceNotFoundException("Examination", "id", request.getExaminationId()));

        if (examination.getStatus() != ExamStatus.APPROVED && examination.getStatus() != ExamStatus.SCHEDULED) {
            throw new BusinessRuleException("Only approved examinations can be scheduled. Current status: " + examination.getStatus());
        }

        // 1. Instructor controls examination duration
        int durationMinutes = (examination.getDurationMinutes() != null && examination.getDurationMinutes() > 0)
                ? examination.getDurationMinutes()
                : (request.getDurationMinutes() != null && request.getDurationMinutes() > 0 ? request.getDurationMinutes() : 60);

        // 2. Exam Officer controls Date, Scheduled Start Time, and College Timezone
        String tzStr = (request.getTimezone() != null && !request.getTimezone().trim().isEmpty())
                ? request.getTimezone().trim()
                : "Africa/Addis_Ababa";
        ZoneId collegeZone;
        try {
            collegeZone = ZoneId.of(tzStr);
        } catch (Exception e) {
            collegeZone = ZoneId.of("Africa/Addis_Ababa");
            tzStr = "Africa/Addis_Ababa";
        }

        LocalDate examDate = request.getExaminationDate();
        LocalTime startTime = request.getScheduledStartTime();

        // Support fallback from scheduledStartAt if legacy request
        if (examDate == null || startTime == null) {
            if (request.getScheduledStartAt() != null) {
                ZonedDateTime zdt = request.getScheduledStartAt().atZone(collegeZone);
                examDate = zdt.toLocalDate();
                startTime = zdt.toLocalTime();
            } else {
                examDate = LocalDate.now(collegeZone);
                startTime = LocalTime.of(9, 0);
            }
        }

        ZonedDateTime startZoned = ZonedDateTime.of(examDate, startTime, collegeZone);
        Instant scheduledStartAt = startZoned.toInstant();
        // Access boundary is 23:59:59 in the configured college timezone on the examination date
        ZonedDateTime accessCloseZoned = ZonedDateTime.of(examDate, LocalTime.of(23, 59, 59), collegeZone);
        Instant startAccessEndsAt = accessCloseZoned.toInstant();

        // 3. Configure and validate 3 Passcodes
        String hash1 = null;
        String hash2 = null;
        String hash3 = null;

        if (request.getPasscode1() != null && request.getPasscode2() != null && request.getPasscode3() != null) {
            passcodeSecurityService.validatePasscodeConfig(
                    request.getPasscode1().trim(),
                    request.getPasscode2().trim(),
                    request.getPasscode3().trim()
            );
            hash1 = passcodeSecurityService.hashPasscode(request.getPasscode1().trim());
            hash2 = passcodeSecurityService.hashPasscode(request.getPasscode2().trim());
            hash3 = passcodeSecurityService.hashPasscode(request.getPasscode3().trim());
        } else {
            // Default seed passcodes ('1234', '5678', '9012') for fallback convenience
            hash1 = passcodeSecurityService.hashPasscode("1234");
            hash2 = passcodeSecurityService.hashPasscode("5678");
            hash3 = passcodeSecurityService.hashPasscode("9012");
        }

        ExamRoom room = null;
        if (request.getRoomId() != null) {
            room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Exam Room", "id", request.getRoomId()));
            if (!Boolean.TRUE.equals(room.getIsActive())) {
                throw new BusinessRuleException("Selected examination room '" + room.getName() + "' is inactive.");
            }
        }

        ExamSchedule schedule = ExamSchedule.builder()
                .examination(examination)
                .examinationDate(examDate)
                .scheduledStartTime(startTime)
                .scheduledStartAt(scheduledStartAt)
                .scheduledCloseAt(startAccessEndsAt)
                .startAccessEndsAt(startAccessEndsAt)
                .durationMinutes(durationMinutes)
                .passcodeHash1(hash1)
                .passcodeHash2(hash2)
                .passcodeHash3(hash3)
                .room(room)
                .timezone(tzStr)
                .status(ScheduleStatus.SCHEDULED)
                .instructions(request.getInstructions() != null ? request.getInstructions().trim() : null)
                .createdBy(officer)
                .build();

        ExamSchedule saved = scheduleRepository.save(schedule);

        // Update examination status to SCHEDULED
        examination.setStatus(ExamStatus.SCHEDULED);
        examinationRepository.save(examination);

        // Automatically generate eligibility from enrolled students in the course offering
        if (Boolean.TRUE.equals(request.getAutoGenerateEligibility()) && examination.getCourseOffering() != null) {
            generateEligibilityFromEnrollments(saved, examination);
        }

        return ExamScheduleDto.fromEntity(saved, true);
    }

    @Transactional(readOnly = true)
    public ScheduleConflictDto checkConflicts(Long examinationId,
                                              Instant start,
                                              Instant close,
                                              Long roomId,
                                              Long excludeScheduleId) {
        ScheduleConflictDto.ScheduleConflictDtoBuilder result = ScheduleConflictDto.builder().hasConflict(false);

        if (roomId != null && start != null && close != null) {
            List<ExamSchedule> roomConflicts = scheduleRepository.findConflictingRoomSchedules(
                    roomId, start, close, ACTIVE_STATUSES, excludeScheduleId
            );
            if (!roomConflicts.isEmpty()) {
                result.hasConflict(true);
                ExamSchedule conflict = roomConflicts.get(0);
                result.roomConflictMessage("Room reserved for: " + conflict.getExamination().getTitle());
            }
        }

        List<ScheduleConflictDto.StudentConflictItem> studentConflicts = new ArrayList<>();
        if (examinationId != null && start != null && close != null) {
            Examination exam = examinationRepository.findById(examinationId).orElse(null);
            if (exam != null && exam.getCourseOffering() != null) {
                List<Enrollment> enrollments = enrollmentRepository.findByCourseOfferingId(exam.getCourseOffering().getId());
                for (Enrollment enrollment : enrollments) {
                    Student student = enrollment.getStudent();
                    if (student != null) {
                        List<ExamEligibility> conflicts = eligibilityRepository.findConflictingStudentSchedules(
                                student.getId(), EligibilityStatus.ELIGIBLE, ACTIVE_STATUSES, start, close, excludeScheduleId
                        );
                        for (ExamEligibility c : conflicts) {
                            ExamSchedule cs = c.getSchedule();
                            studentConflicts.add(ScheduleConflictDto.StudentConflictItem.builder()
                                    .studentId(student.getId())
                                    .studentIdNumber(student.getRegistrationNumber())
                                    .studentName(student.getUser() != null ? student.getUser().getFirstName() + " " + student.getUser().getLastName() : "Student")
                                    .conflictingScheduleId(cs.getId())
                                    .conflictingExamTitle(cs.getExamination().getTitle())
                                    .conflictingStart(cs.getScheduledStartAt())
                                    .conflictingClose(cs.getScheduledCloseAt())
                                    .build());
                        }
                    }
                }
            }
        }

        if (!studentConflicts.isEmpty()) {
            result.hasConflict(true);
            result.studentConflicts(studentConflicts);
        }

        return result.build();
    }

    @Transactional
    public ExamScheduleDto reschedule(Long scheduleId, RescheduleRequest request, Long officerId) {
        ExamSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Schedule", "id", scheduleId));

        if (schedule.getStatus() == ScheduleStatus.COMPLETED || schedule.getStatus() == ScheduleStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot reschedule an examination that is already " + schedule.getStatus());
        }

        ZoneId collegeZone = ZoneId.of(schedule.getTimezone() != null ? schedule.getTimezone() : "Africa/Addis_Ababa");
        LocalDate examDate = request.getExaminationDate() != null
                ? request.getExaminationDate()
                : schedule.getExaminationDate();
        LocalTime startTime = request.getScheduledStartTime() != null
                ? request.getScheduledStartTime()
                : schedule.getScheduledStartTime();

        if (examDate != null && startTime != null) {
            schedule.setExaminationDate(examDate);
            schedule.setScheduledStartTime(startTime);
            Instant startInstant = ZonedDateTime.of(examDate, startTime, collegeZone).toInstant();
            Instant accessCloseInstant = ZonedDateTime.of(examDate, LocalTime.of(23, 59, 59), collegeZone).toInstant();
            schedule.setScheduledStartAt(startInstant);
            schedule.setStartAccessEndsAt(accessCloseInstant);
            schedule.setScheduledCloseAt(accessCloseInstant);
        } else if (request.getScheduledStartAt() != null) {
            schedule.setScheduledStartAt(request.getScheduledStartAt());
        }

        if (request.getRoomId() != null) {
            ExamRoom room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Exam Room", "id", request.getRoomId()));
            schedule.setRoom(room);
        }

        if (request.getInstructions() != null) {
            schedule.setInstructions(request.getInstructions().trim());
        }

        schedule.setStatus(ScheduleStatus.RESCHEDULED);
        ExamSchedule updated = scheduleRepository.save(schedule);

        emitScheduleNotifications(
                updated,
                NotificationType.EXAM_RESCHEDULED,
                "Examination Rescheduled: " + updated.getExamination().getTitle(),
                "Your examination date/time has been rescheduled to " + updated.getExaminationDate() + " at " + updated.getScheduledStartTime() + " (" + updated.getTimezone() + ")."
        );

        return ExamScheduleDto.fromEntity(updated, true);
    }

    @Transactional
    public ExamScheduleDto cancelSchedule(Long scheduleId, String reason, Long officerId) {
        ExamSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Schedule", "id", scheduleId));

        schedule.setStatus(ScheduleStatus.CANCELLED);
        ExamSchedule updated = scheduleRepository.save(schedule);

        String msg = "The scheduled examination for " + updated.getExamination().getTitle() + " has been cancelled by the examination office."
                + (reason != null && !reason.trim().isEmpty() ? " Reason: " + reason.trim() : "");

        emitScheduleNotifications(
                updated,
                NotificationType.EXAM_CANCELLED,
                "Examination Cancelled: " + updated.getExamination().getTitle(),
                msg
        );

        return ExamScheduleDto.fromEntity(updated, true);
    }

    @Transactional
    public ExamEligibilityDto addStudentEligibility(Long scheduleId, AddStudentEligibilityRequest request, Long officerId) {
        ExamSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Schedule", "id", scheduleId));

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student", "id", request.getStudentId()));

        if (eligibilityRepository.existsByScheduleIdAndStudentId(scheduleId, request.getStudentId())) {
            throw new BusinessRuleException("Student is already registered for this examination schedule.");
        }

        ExamEligibility eligibility = ExamEligibility.builder()
                .schedule(schedule)
                .student(student)
                .eligibilityStatus(request.getEligibilityStatus() != null ? request.getEligibilityStatus() : EligibilityStatus.ELIGIBLE)
                .reason(request.getReason())
                .build();

        ExamEligibility saved = eligibilityRepository.save(eligibility);

        // Ensure student is enrolled in the course offering if not already
        if (schedule.getExamination() != null && schedule.getExamination().getCourseOffering() != null) {
            CourseOffering co = schedule.getExamination().getCourseOffering();
            if (!enrollmentRepository.existsByStudentIdAndCourseOfferingId(student.getId(), co.getId())) {
                Enrollment enrollment = Enrollment.builder()
                        .student(student)
                        .courseOffering(co)
                        .status(EnrollmentStatus.ACTIVE)
                        .enrolledAt(Instant.now())
                        .build();
                enrollmentRepository.save(enrollment);
            }
        }

        return ExamEligibilityDto.fromEntity(saved);
    }

    @Transactional
    public ExamEligibilityDto updateEligibility(Long scheduleId, Long studentId, UpdateEligibilityRequest request, Long officerId) {
        ExamEligibility eligibility = eligibilityRepository.findByScheduleIdAndStudentId(scheduleId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Eligibility", "scheduleId and studentId", scheduleId + ", " + studentId));

        eligibility.setEligibilityStatus(request.getEligibilityStatus());
        if (request.getReason() != null) {
            eligibility.setReason(request.getReason().trim());
        }

        ExamEligibility updated = eligibilityRepository.save(eligibility);
        return ExamEligibilityDto.fromEntity(updated);
    }

    @Transactional
    public void removeStudentEligibility(Long scheduleId, Long studentId, Long officerId) {
        ExamEligibility eligibility = eligibilityRepository.findByScheduleIdAndStudentId(scheduleId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Eligibility", "scheduleId and studentId", scheduleId + ", " + studentId));
        eligibilityRepository.delete(eligibility);
    }

    /**
     * Authoritative Student Examination Visibility Engine:
     * Evaluates schedule date, start time, access window, individual attempt states, and resume requirements.
     */
    @Transactional(readOnly = true)
    public List<StudentScheduleDto> getStudentSchedules(Long userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessRuleException("Authenticated user is not registered as a student."));

        List<ExamEligibility> eligibilities = eligibilityRepository.findByStudentUserIdWithSchedule(userId);
        Instant now = Instant.now();

        return eligibilities.stream()
                .map(ee -> {
                    ExamSchedule s = ee.getSchedule();
                    Examination ex = s.getExamination();

                    ZoneId collegeZone = ZoneId.of(s.getTimezone() != null ? s.getTimezone() : "Africa/Addis_Ababa");
                    LocalDate collegeToday = LocalDate.now(collegeZone);

                    LocalDate examDate = s.getExaminationDate() != null
                            ? s.getExaminationDate()
                            : s.getScheduledStartAt().atZone(collegeZone).toLocalDate();

                    Instant startAccessEndsAt = s.getStartAccessEndsAt() != null
                            ? s.getStartAccessEndsAt()
                            : ZonedDateTime.of(examDate, LocalTime.of(23, 59, 59), collegeZone).toInstant();

                    // Check existing attempts for this student & schedule
                    List<ExamAttempt> attempts = attemptRepository.findByStudentIdAndScheduleId(student.getId(), s.getId());
                    ExamAttempt latestAttempt = attempts.isEmpty() ? null : attempts.get(attempts.size() - 1);

                    AttemptStatus attemptStatus = latestAttempt != null ? latestAttempt.getStatus() : AttemptStatus.NOT_STARTED;
                    Long attemptId = latestAttempt != null ? latestAttempt.getId() : null;
                    Integer resumeStage = latestAttempt != null ? latestAttempt.getResumeStage() : 0;
                    Integer remainingSecs = latestAttempt != null ? latestAttempt.getRemainingActiveSeconds() : (ex.getDurationMinutes() * 60);

                    boolean isExamDate = collegeToday.equals(examDate);
                    boolean isBeforeStart = isExamDate && now.isBefore(s.getScheduledStartAt());
                    boolean isWindowOpen = isExamDate && !now.isBefore(s.getScheduledStartAt()) && !now.isAfter(startAccessEndsAt);
                    boolean isWindowClosed = collegeToday.isAfter(examDate) || now.isAfter(startAccessEndsAt);

                    boolean isSubmitted = latestAttempt != null
                            && (attemptStatus == AttemptStatus.SUBMITTED || attemptStatus == AttemptStatus.AUTO_SUBMITTED || attemptStatus == AttemptStatus.EXPIRED);

                    boolean isResumable = !isSubmitted
                            && latestAttempt != null
                            && (attemptStatus == AttemptStatus.CONNECTION_PAUSED || attemptStatus == AttemptStatus.RESUME_REQUIRED || attemptStatus == AttemptStatus.ACTIVE || attemptStatus == AttemptStatus.IN_PROGRESS)
                            && remainingSecs != null && remainingSecs > 0;

                    boolean isStartable = !isSubmitted
                            && latestAttempt == null
                            && isWindowOpen
                            && (s.getStatus() == ScheduleStatus.SCHEDULED || s.getStatus() == ScheduleStatus.RESCHEDULED)
                            && ee.getEligibilityStatus() == EligibilityStatus.ELIGIBLE;

                    // Available on scheduled examination date strictly if not submitted and active/startable
                    boolean isAvailable = !isSubmitted
                            && (isResumable || isStartable)
                            && (s.getStatus() == ScheduleStatus.SCHEDULED || s.getStatus() == ScheduleStatus.RESCHEDULED)
                            && ee.getEligibilityStatus() == EligibilityStatus.ELIGIBLE;

                    StudentScheduleDto.StudentScheduleDtoBuilder b = StudentScheduleDto.builder()
                            .scheduleId(s.getId())
                            .examinationId(ex.getId())
                            .examinationTitle(ex.getTitle())
                            .examinationDescription(ex.getDescription())
                            .examinationDate(examDate)
                            .scheduledStartTime(s.getScheduledStartTime())
                            .scheduledStartAt(s.getScheduledStartAt())
                            .scheduledCloseAt(s.getScheduledCloseAt())
                            .startAccessEndsAt(startAccessEndsAt)
                            .timezone(s.getTimezone())
                            .durationMinutes(ex.getDurationMinutes())
                            .totalMarks(ex.getTotalMarks())
                            .passingMarks(ex.getPassingMarks())
                            .status(s.getStatus())
                            .eligibilityStatus(ee.getEligibilityStatus())
                            .eligibilityReason(ee.getReason())
                            .instructions(s.getInstructions())
                            .isAvailableNow(isAvailable)
                            .isStartableNow(isStartable)
                            .isResumableNow(isResumable)
                            .isBeforeScheduledStart(isBeforeStart && latestAttempt == null)
                            .isStartWindowClosed(isWindowClosed && latestAttempt == null)
                            .attemptStatus(attemptStatus)
                            .attemptId(attemptId)
                            .resumeStage(resumeStage)
                            .remainingActiveSeconds(remainingSecs);

                    if (s.getRoom() != null) {
                        b.roomName(s.getRoom().getName()).building(s.getRoom().getBuilding());
                    }

                    if (ex.getCourseOffering() != null) {
                        b.section(ex.getCourseOffering().getSection());
                        if (ex.getCourseOffering().getCourse() != null) {
                            b.courseCode(ex.getCourseOffering().getCourse().getCode())
                             .courseName(ex.getCourseOffering().getCourse().getName());
                        }
                        if (ex.getCourseOffering().getSemester() != null) {
                            b.semesterName(ex.getCourseOffering().getSemester().getName());
                        }
                    }

                    return b.build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamNotificationDto> getNotificationsForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ExamNotificationDto::fromEntity)
                .toList();
    }

    private void generateEligibilityFromEnrollments(ExamSchedule schedule, Examination examination) {
        Long offeringId = examination.getCourseOffering().getId();
        List<Enrollment> enrollments = enrollmentRepository.findByCourseOfferingId(offeringId);

        for (Enrollment en : enrollments) {
            Student student = en.getStudent();
            if (student != null && !eligibilityRepository.existsByScheduleIdAndStudentId(schedule.getId(), student.getId())) {
                ExamEligibility eligibility = ExamEligibility.builder()
                        .schedule(schedule)
                        .student(student)
                        .eligibilityStatus(EligibilityStatus.ELIGIBLE)
                        .reason("Enrolled in " + examination.getCourseOffering().getCourse().getCode() + " Section " + examination.getCourseOffering().getSection())
                        .build();

                schedule.getEligibilities().add(eligibility);
                eligibilityRepository.save(eligibility);

                if (student.getUser() != null) {
                    try {
                        notificationService.sendNotificationToUser(
                                student.getUser(),
                                NotificationType.EXAM_SCHEDULED,
                                "Examination Scheduled: " + examination.getTitle(),
                                "Your examination for " + examination.getCourseOffering().getCourse().getCode() +
                                        " has been scheduled for " + schedule.getExaminationDate() + " at " + schedule.getScheduledStartTime() + " (" + schedule.getTimezone() + ").",
                                schedule
                        );
                    } catch (Exception e) {
                        log.warn("Failed to deliver real-time schedule notification: {}", e.getMessage());
                    }
                }
            }
        }
    }

    private void emitScheduleNotifications(ExamSchedule schedule, NotificationType type, String title, String message) {
        for (ExamEligibility ee : schedule.getEligibilities()) {
            if (ee.getStudent() != null && ee.getStudent().getUser() != null) {
                try {
                    notificationService.sendNotificationToUser(
                            ee.getStudent().getUser(),
                            type,
                            title,
                            message,
                            schedule
                    );
                } catch (Exception e) {
                    log.warn("Failed to deliver real-time schedule notification: {}", e.getMessage());
                }
            }
        }
    }
}
