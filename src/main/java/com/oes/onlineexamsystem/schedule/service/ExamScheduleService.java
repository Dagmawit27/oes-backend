package com.oes.onlineexamsystem.schedule.service;

import com.oes.onlineexamsystem.academic.entity.Enrollment;
import com.oes.onlineexamsystem.academic.entity.Student;
import com.oes.onlineexamsystem.academic.repository.EnrollmentRepository;
import com.oes.onlineexamsystem.academic.repository.StudentRepository;
import com.oes.onlineexamsystem.common.dto.PagedResponse;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.examination.entity.ExamStatus;
import com.oes.onlineexamsystem.examination.entity.Examination;
import com.oes.onlineexamsystem.examination.repository.ExaminationRepository;
import com.oes.onlineexamsystem.schedule.dto.*;
import com.oes.onlineexamsystem.schedule.entity.*;
import com.oes.onlineexamsystem.schedule.repository.ExamEligibilityRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamNotificationRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamRoomRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamScheduleRepository;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

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
                               UserRepository userRepository) {
        this.scheduleRepository = scheduleRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.roomRepository = roomRepository;
        this.notificationRepository = notificationRepository;
        this.examinationRepository = examinationRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    /**
     * Official Attempt Deadline Rule:
     * Enforces the earlier of the student's personal duration deadline and the examination window closing time.
     */
    public static Instant calculateOfficialAttemptDeadline(Instant attemptStart, int durationMinutes, Instant scheduledCloseAt) {
        Instant personalDeadline = attemptStart.plus(durationMinutes, ChronoUnit.MINUTES);
        return personalDeadline.isBefore(scheduledCloseAt) ? personalDeadline : scheduledCloseAt;
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
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledCloseAt"), to));
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

        if (!request.getScheduledCloseAt().isAfter(request.getScheduledStartAt())) {
            throw new BusinessRuleException("Scheduled close time must be strictly after scheduled start time.");
        }

        if (request.getDurationMinutes() <= 0) {
            throw new BusinessRuleException("Examination duration must be greater than zero.");
        }

        ExamRoom room = null;
        if (request.getRoomId() != null) {
            room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Exam Room", "id", request.getRoomId()));
            if (!Boolean.TRUE.equals(room.getIsActive())) {
                throw new BusinessRuleException("Selected examination room '" + room.getName() + "' is inactive.");
            }

            // Room conflict verification
            List<ExamSchedule> roomConflicts = scheduleRepository.findConflictingRoomSchedules(
                    room.getId(), request.getScheduledStartAt(), request.getScheduledCloseAt(), ACTIVE_STATUSES, null
            );
            if (!roomConflicts.isEmpty()) {
                throw new BusinessRuleException("Room '" + room.getName() + "' is already reserved for examination '" +
                        roomConflicts.get(0).getExamination().getTitle() + "' during the specified time window.");
            }
        }

        ExamSchedule schedule = ExamSchedule.builder()
                .examination(examination)
                .scheduledStartAt(request.getScheduledStartAt())
                .scheduledCloseAt(request.getScheduledCloseAt())
                .durationMinutes(request.getDurationMinutes())
                .room(room)
                .timezone(request.getTimezone() != null ? request.getTimezone() : "UTC")
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

        // 1. Room conflict check
        if (roomId != null) {
            List<ExamSchedule> roomConflicts = scheduleRepository.findConflictingRoomSchedules(
                    roomId, start, close, ACTIVE_STATUSES, excludeScheduleId
            );
            if (!roomConflicts.isEmpty()) {
                result.hasConflict(true);
                ExamSchedule conflict = roomConflicts.get(0);
                result.roomConflictMessage("Room reserved for: " + conflict.getExamination().getTitle() +
                        " (" + conflict.getScheduledStartAt() + " to " + conflict.getScheduledCloseAt() + ")");
            }
        }

        // 2. Student conflict check
        List<ScheduleConflictDto.StudentConflictItem> studentConflicts = new ArrayList<>();
        if (examinationId != null) {
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
            throw new BusinessRuleException("Cannot reschedule an examination that is " + schedule.getStatus() + ".");
        }

        if (!request.getScheduledCloseAt().isAfter(request.getScheduledStartAt())) {
            throw new BusinessRuleException("Scheduled close time must be strictly after scheduled start time.");
        }

        if (request.getRoomId() != null) {
            ExamRoom room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Exam Room", "id", request.getRoomId()));
            List<ExamSchedule> roomConflicts = scheduleRepository.findConflictingRoomSchedules(
                    room.getId(), request.getScheduledStartAt(), request.getScheduledCloseAt(), ACTIVE_STATUSES, scheduleId
            );
            if (!roomConflicts.isEmpty()) {
                throw new BusinessRuleException("Room '" + room.getName() + "' is already reserved for examination '" +
                        roomConflicts.get(0).getExamination().getTitle() + "'.");
            }
            schedule.setRoom(room);
        } else if (request.getRoomId() == null && schedule.getRoom() != null) {
            schedule.setRoom(null);
        }

        schedule.setScheduledStartAt(request.getScheduledStartAt());
        schedule.setScheduledCloseAt(request.getScheduledCloseAt());
        if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
            schedule.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getInstructions() != null) {
            schedule.setInstructions(request.getInstructions().trim());
        }
        schedule.setStatus(ScheduleStatus.RESCHEDULED);

        ExamSchedule updated = scheduleRepository.save(schedule);

        // Notify affected students
        emitScheduleNotifications(updated, NotificationType.EXAM_RESCHEDULED,
                "Examination Rescheduled: " + schedule.getExamination().getTitle(),
                "Your examination has been rescheduled to " + request.getScheduledStartAt() +
                        (request.getReason() != null ? ". Reason: " + request.getReason() : "."));

        return ExamScheduleDto.fromEntity(updated, true);
    }

    @Transactional
    public ExamScheduleDto cancelSchedule(Long scheduleId, String reason, Long officerId) {
        ExamSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Schedule", "id", scheduleId));

        if (schedule.getStatus() == ScheduleStatus.CANCELLED) {
            throw new BusinessRuleException("Schedule is already cancelled.");
        }

        schedule.setStatus(ScheduleStatus.CANCELLED);
        ExamSchedule updated = scheduleRepository.save(schedule);

        // Notify affected students
        emitScheduleNotifications(updated, NotificationType.EXAM_CANCELLED,
                "Examination Cancelled: " + schedule.getExamination().getTitle(),
                "The scheduled examination session has been cancelled." +
                        (reason != null ? " Reason: " + reason : ""));

        return ExamScheduleDto.fromEntity(updated, true);
    }

    @Transactional
    public ExamEligibilityDto addStudentEligibility(Long scheduleId, AddStudentEligibilityRequest request, Long officerId) {
        ExamSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Schedule", "id", scheduleId));

        Student student = studentRepository.findById(request.getStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student", "id", request.getStudentId()));

        if (eligibilityRepository.existsByScheduleIdAndStudentId(scheduleId, student.getId())) {
            throw new BusinessRuleException("Student " + student.getRegistrationNumber() + " is already registered for this examination schedule.");
        }

        ExamEligibility eligibility = ExamEligibility.builder()
                .schedule(schedule)
                .student(student)
                .eligibilityStatus(request.getEligibilityStatus() != null ? request.getEligibilityStatus() : EligibilityStatus.ELIGIBLE)
                .reason(request.getReason() != null ? request.getReason().trim() : "Authorized exception by exam officer")
                .build();

        ExamEligibility saved = eligibilityRepository.save(eligibility);
        return ExamEligibilityDto.fromEntity(saved);
    }

    @Transactional
    public ExamEligibilityDto updateEligibility(Long scheduleId, Long studentId, UpdateEligibilityRequest request, Long officerId) {
        ExamEligibility eligibility = eligibilityRepository.findByScheduleIdAndStudentId(scheduleId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Eligibility", "studentId", studentId));

        eligibility.setEligibilityStatus(request.getEligibilityStatus());
        if (request.getReason() != null) {
            eligibility.setReason(request.getReason().trim());
        }

        ExamEligibility updated = eligibilityRepository.save(eligibility);
        return ExamEligibilityDto.fromEntity(updated);
    }

    @Transactional
    public void removeStudentEligibility(Long scheduleId, Long studentId, Long officerId) {
        if (!eligibilityRepository.existsByScheduleIdAndStudentId(scheduleId, studentId)) {
            throw new ResourceNotFoundException("Exam Eligibility", "studentId", studentId);
        }
        eligibilityRepository.deleteByScheduleIdAndStudentId(scheduleId, studentId);
    }

    @Transactional(readOnly = true)
    public List<StudentScheduleDto> getStudentSchedules(Long userId) {
        Instant now = Instant.now();
        List<ExamEligibility> eligibilities = eligibilityRepository.findByStudentUserIdWithSchedule(userId);

        return eligibilities.stream()
                .map(ee -> {
                    ExamSchedule s = ee.getSchedule();
                    Examination ex = s.getExamination();

                    boolean isAvailable = now.isAfter(s.getScheduledStartAt())
                            && now.isBefore(s.getScheduledCloseAt())
                            && (s.getStatus() == ScheduleStatus.SCHEDULED || s.getStatus() == ScheduleStatus.RESCHEDULED)
                            && ee.getEligibilityStatus() == EligibilityStatus.ELIGIBLE;

                    Instant effectiveDeadline = calculateOfficialAttemptDeadline(now, s.getDurationMinutes(), s.getScheduledCloseAt());

                    StudentScheduleDto.StudentScheduleDtoBuilder b = StudentScheduleDto.builder()
                            .scheduleId(s.getId())
                            .examinationId(ex.getId())
                            .examinationTitle(ex.getTitle())
                            .examinationDescription(ex.getDescription())
                            .scheduledStartAt(s.getScheduledStartAt())
                            .scheduledCloseAt(s.getScheduledCloseAt())
                            .durationMinutes(s.getDurationMinutes())
                            .totalMarks(ex.getTotalMarks())
                            .passingMarks(ex.getPassingMarks())
                            .status(s.getStatus())
                            .eligibilityStatus(ee.getEligibilityStatus())
                            .eligibilityReason(ee.getReason())
                            .instructions(s.getInstructions())
                            .isAvailableNow(isAvailable)
                            .effectiveDeadline(effectiveDeadline);

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

                // Create notification event
                if (student.getUser() != null) {
                    ExamNotification notification = ExamNotification.builder()
                            .schedule(schedule)
                            .user(student.getUser())
                            .eventType(NotificationType.EXAM_SCHEDULED)
                            .title("Examination Scheduled: " + examination.getTitle())
                            .message("Your examination for " + examination.getCourseOffering().getCourse().getCode() +
                                    " has been scheduled for " + schedule.getScheduledStartAt() + ". Please check your exam schedule.")
                            .isRead(false)
                            .build();
                    notificationRepository.save(notification);
                }
            }
        }
    }

    private void emitScheduleNotifications(ExamSchedule schedule, NotificationType type, String title, String message) {
        for (ExamEligibility ee : schedule.getEligibilities()) {
            if (ee.getStudent() != null && ee.getStudent().getUser() != null) {
                ExamNotification notification = ExamNotification.builder()
                        .schedule(schedule)
                        .user(ee.getStudent().getUser())
                        .eventType(type)
                        .title(title)
                        .message(message)
                        .isRead(false)
                        .build();
                notificationRepository.save(notification);
            }
        }
    }
}
