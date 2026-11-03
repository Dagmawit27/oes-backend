package com.oes.onlineexamsystem.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.auth.dto.LoginRequest;
import com.oes.onlineexamsystem.schedule.dto.CreateScheduleRequest;
import com.oes.onlineexamsystem.schedule.dto.RescheduleRequest;
import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import com.oes.onlineexamsystem.schedule.entity.ScheduleStatus;
import com.oes.onlineexamsystem.schedule.repository.ExamScheduleRepository;
import com.oes.onlineexamsystem.schedule.service.ExamScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamScheduleRepository scheduleRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private String getAuthToken(String email) throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password("Admin@123")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    @DisplayName("Exam Officer can list schedules and calendar view")
    void testGetSchedulesAndCalendar_Success() throws Exception {
        String officerToken = getAuthToken("officer@college.edu");

        // 1. List schedules
        mockMvc.perform(get("/api/v1/schedules")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));

        // 2. Calendar view
        Instant now = Instant.now();
        Instant end = now.plus(14, ChronoUnit.DAYS);

        mockMvc.perform(get("/api/v1/schedules/calendar")
                        .header("Authorization", "Bearer " + officerToken)
                        .param("start", now.toString())
                        .param("end", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Exam Officer can schedule an approved examination with automatic student eligibility")
    void testCreateSchedule_Success() throws Exception {
        String officerToken = getAuthToken("officer@college.edu");

        Instant start = Instant.now().plus(45, ChronoUnit.DAYS);
        Instant close = start.plus(3, ChronoUnit.HOURS);

        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .examinationId(1L) // Seeded approved CS101 Midterm
                .scheduledStartAt(start)
                .scheduledCloseAt(close)
                .durationMinutes(60)
                .roomId(3L) // Auditorium Main
                .instructions("Arrive 10 minutes early with student ID.")
                .autoGenerateEligibility(true)
                .build();

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.eligibleStudentCount", greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Scheduling a non-approved examination throws 400 Bad Request")
    void testScheduleNonApprovedExam_Fails() throws Exception {
        String officerToken = getAuthToken("officer@college.edu");

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant close = start.plus(2, ChronoUnit.HOURS);

        // Examination 2 is seeded as DRAFT
        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .examinationId(2L)
                .scheduledStartAt(start)
                .scheduledCloseAt(close)
                .durationMinutes(30)
                .build();

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Only approved examinations can be scheduled")));
    }

    @Test
    @DisplayName("Scheduling with close time before start time fails validation")
    void testScheduleInvalidTime_Fails() throws Exception {
        String officerToken = getAuthToken("officer@college.edu");

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant close = start.minus(1, ChronoUnit.HOURS); // Close before start!

        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .examinationId(1L)
                .scheduledStartAt(start)
                .scheduledCloseAt(close)
                .durationMinutes(60)
                .build();

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Scheduled close time must be strictly after")));
    }

    @Test
    @DisplayName("Room conflict detection prevents double booking of physical room")
    void testRoomConflict_PreventsDoubleBooking() throws Exception {
        String officerToken = getAuthToken("officer@college.edu");

        ExamSchedule existing = scheduleRepository.findAll().stream()
                .filter(s -> s.getRoom() != null && s.getStatus() == ScheduleStatus.SCHEDULED)
                .findFirst()
                .orElseThrow();

        Instant conflictStart = existing.getScheduledStartAt().plus(10, ChronoUnit.MINUTES);
        Instant conflictClose = conflictStart.plus(30, ChronoUnit.MINUTES);

        CreateScheduleRequest conflictingRequest = CreateScheduleRequest.builder()
                .examinationId(1L)
                .scheduledStartAt(conflictStart)
                .scheduledCloseAt(conflictClose)
                .durationMinutes(30)
                .roomId(existing.getRoom().getId())
                .build();

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictingRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already reserved for examination")));
    }

    @Test
    @DisplayName("Reschedule and Cancel Schedule lifecycle")
    void testRescheduleAndCancelLifecycle() throws Exception {
        String officerToken = getAuthToken("officer@college.edu");

        // Step 1: Create a temporary schedule
        Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
        Instant close = start.plus(3, ChronoUnit.HOURS);

        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .examinationId(1L)
                .scheduledStartAt(start)
                .scheduledCloseAt(close)
                .durationMinutes(60)
                .roomId(3L) // Auditorium
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Number scheduleIdNum = com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");
        Long scheduleId = scheduleIdNum.longValue();

        // Step 2: Reschedule to new time
        Instant newStart = start.plus(1, ChronoUnit.DAYS);
        Instant newClose = newStart.plus(2, ChronoUnit.HOURS);

        RescheduleRequest rescheduleRequest = RescheduleRequest.builder()
                .scheduledStartAt(newStart)
                .scheduledCloseAt(newClose)
                .durationMinutes(50)
                .reason("Lab equipment maintenance on original date")
                .build();

        mockMvc.perform(post("/api/v1/schedules/" + scheduleId + "/reschedule")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rescheduleRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESCHEDULED"));

        // Step 3: Cancel schedule
        mockMvc.perform(post("/api/v1/schedules/" + scheduleId + "/cancel")
                        .header("Authorization", "Bearer " + officerToken)
                        .param("reason", "Cancelled due to university holiday"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Student Portal: Student can view their upcoming schedules and notifications")
    void testStudentPortal_SchedulesAndNotifications() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        // Student schedules
        mockMvc.perform(get("/api/v1/schedules/my-schedules")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].courseCode").value("CS101"));

        // Student notifications
        mockMvc.perform(get("/api/v1/schedules/my-notifications")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Official attempt deadline calculation adheres to min(attemptStart + duration, windowClose)")
    void testOfficialAttemptDeadlineCalculation() {
        Instant windowClose = Instant.parse("2026-11-10T12:00:00Z");

        // Case A: Student starts early (10:00). 60 min duration ends at 11:00, which is before 12:00 window close.
        Instant startEarly = Instant.parse("2026-11-10T10:00:00Z");
        Instant deadlineEarly = ExamScheduleService.calculateOfficialAttemptDeadline(startEarly, 60, windowClose);
        assertEquals(Instant.parse("2026-11-10T11:00:00Z"), deadlineEarly);

        // Case B: Student starts close to window close (11:45). 60 min duration would be 12:45, but hard deadline is 12:00.
        Instant startLate = Instant.parse("2026-11-10T11:45:00Z");
        Instant deadlineLate = ExamScheduleService.calculateOfficialAttemptDeadline(startLate, 60, windowClose);
        assertEquals(Instant.parse("2026-11-10T12:00:00Z"), deadlineLate);
    }

    @Test
    @DisplayName("Student cannot create or cancel schedules (403 Forbidden)")
    void testStudentSchedule_Forbidden() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .examinationId(1L)
                .scheduledStartAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .scheduledCloseAt(Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS))
                .durationMinutes(60)
                .build();

        mockMvc.perform(post("/api/v1/schedules")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/schedules/1/cancel")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
