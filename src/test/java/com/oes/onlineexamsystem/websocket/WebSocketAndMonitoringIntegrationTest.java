package com.oes.onlineexamsystem.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.auth.dto.LoginRequest;
import com.oes.onlineexamsystem.auth.security.JwtTokenProvider;
import com.oes.onlineexamsystem.execution.dto.ExamEventLogRequest;
import com.oes.onlineexamsystem.execution.entity.ExamEventType;
import com.oes.onlineexamsystem.execution.repository.ExamAttemptRepository;
import com.oes.onlineexamsystem.notification.dto.AnnouncementRequest;
import com.oes.onlineexamsystem.notification.service.NotificationService;
import com.oes.onlineexamsystem.schedule.entity.ExamNotification;
import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import com.oes.onlineexamsystem.schedule.entity.NotificationType;
import com.oes.onlineexamsystem.schedule.repository.ExamNotificationRepository;
import com.oes.onlineexamsystem.schedule.repository.ExamScheduleRepository;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import com.oes.onlineexamsystem.websocket.security.WebSocketAuthChannelInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WebSocketAndMonitoringIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private WebSocketAuthChannelInterceptor authChannelInterceptor;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ExamNotificationRepository notificationRepository;

    @Autowired
    private ExamScheduleRepository scheduleRepository;

    @Autowired
    private ExamAttemptRepository attemptRepository;

    @Autowired
    private UserRepository userRepository;

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

    @BeforeEach
    void setUp() {
        attemptRepository.deleteAll();
    }

    @Test
    @DisplayName("STOMP Interceptor: Authenticates valid JWT and sets Security Context")
    void testStompConnectAuthenticationSuccess() throws Exception {
        String token = getAuthToken("officer@college.edu");

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> handled = authChannelInterceptor.preSend(message, (MessageChannel) (msg, timeout) -> true);
        assertNotNull(handled);

        StompHeaderAccessor handledAccessor = StompHeaderAccessor.wrap(handled);
        Authentication user = (Authentication) handledAccessor.getUser();
        assertNotNull(user);
        assertEquals("officer@college.edu", user.getName());
        assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EXAM_OFFICER")));
    }

    @Test
    @DisplayName("STOMP Interceptor: Rejects connection with missing or invalid token")
    void testStompConnectAuthenticationFailure() {
        // Missing token
        StompHeaderAccessor accessorWithoutToken = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> msgWithoutToken = MessageBuilder.createMessage(new byte[0], accessorWithoutToken.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                authChannelInterceptor.preSend(msgWithoutToken, (MessageChannel) (msg, timeout) -> true));

        // Invalid token
        StompHeaderAccessor accessorWithInvalidToken = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessorWithInvalidToken.setNativeHeader("Authorization", "Bearer invalid-tampered-token");
        Message<?> msgWithInvalidToken = MessageBuilder.createMessage(new byte[0], accessorWithInvalidToken.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () ->
                authChannelInterceptor.preSend(msgWithInvalidToken, (MessageChannel) (msg, timeout) -> true));
    }

    @Test
    @DisplayName("STOMP Interceptor: Enforces role authorization on Live Monitoring subscriptions")
    void testStompSubscriptionAuthorization() throws Exception {
        String studentToken = getAuthToken("student@college.edu");
        String officerToken = getAuthToken("officer@college.edu");

        // 1. Student trying to subscribe to live monitoring -> FORBIDDEN
        StompHeaderAccessor studentAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        studentAccessor.setNativeHeader("Authorization", "Bearer " + studentToken);
        studentAccessor.setLeaveMutable(true);
        Message<?> handledStudentConnect = authChannelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], studentAccessor.getMessageHeaders()), (MessageChannel) (msg, t) -> true);
        Authentication studentAuth = (Authentication) StompHeaderAccessor.wrap(handledStudentConnect).getUser();

        StompHeaderAccessor studentSubAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        studentSubAccessor.setUser(studentAuth);
        studentSubAccessor.setDestination("/topic/monitoring/schedules/1");
        Message<?> studentSubMsg = MessageBuilder.createMessage(new byte[0], studentSubAccessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                authChannelInterceptor.preSend(studentSubMsg, (MessageChannel) (msg, t) -> true));

        // 2. Exam Officer subscribing to live monitoring -> ALLOWED
        StompHeaderAccessor officerAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        officerAccessor.setNativeHeader("Authorization", "Bearer " + officerToken);
        officerAccessor.setLeaveMutable(true);
        Message<?> handledOfficerConnect = authChannelInterceptor.preSend(MessageBuilder.createMessage(new byte[0], officerAccessor.getMessageHeaders()), (MessageChannel) (msg, t) -> true);
        Authentication officerAuth = (Authentication) StompHeaderAccessor.wrap(handledOfficerConnect).getUser();

        StompHeaderAccessor officerSubAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        officerSubAccessor.setUser(officerAuth);
        officerSubAccessor.setDestination("/topic/monitoring/schedules/1");
        Message<?> officerSubMsg = MessageBuilder.createMessage(new byte[0], officerSubAccessor.getMessageHeaders());

        assertDoesNotThrow(() ->
                authChannelInterceptor.preSend(officerSubMsg, (MessageChannel) (msg, t) -> true));

        // 3. Student subscribing to other student's private destination -> FORBIDDEN
        StompHeaderAccessor invalidPrivateSubAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        invalidPrivateSubAccessor.setUser(studentAuth);
        invalidPrivateSubAccessor.setDestination("/user/otherstudent@college.edu/queue/notifications");
        Message<?> invalidPrivateSubMsg = MessageBuilder.createMessage(new byte[0], invalidPrivateSubAccessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () ->
                authChannelInterceptor.preSend(invalidPrivateSubMsg, (MessageChannel) (msg, t) -> true));
    }

    @Test
    @DisplayName("Notification REST APIs: Unread count, Mark read, Mark all read, and Announcement security")
    void testNotificationRestApis() throws Exception {
        String studentToken = getAuthToken("student@college.edu");
        String officerToken = getAuthToken("officer@college.edu");

        User student = userRepository.findByEmailIgnoreCase("student@college.edu").orElseThrow();

        // Seed 2 unread notifications for student
        notificationService.sendNotificationToUser(student, NotificationType.EXAM_SCHEDULED,
                "Exam 1 Scheduled", "Your exam 1 is ready", null);
        ExamNotification notif2 = notificationRepository.save(ExamNotification.builder()
                .user(student)
                .eventType(NotificationType.ANNOUNCEMENT)
                .title("College Announcement")
                .message("Campus will close early today")
                .isRead(false)
                .build());

        // 1. Check unread count
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount", greaterThanOrEqualTo(2)));

        // 2. Fetch notifications
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", not(empty())));

        // 3. Mark single notification as read
        mockMvc.perform(put("/api/v1/notifications/" + notif2.getId() + "/read")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead", is(true)));

        // 4. Mark all as read
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount", greaterThanOrEqualTo(1)));

        // Verify unread count is now 0
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount", is(0)));

        // 5. Student cannot broadcast announcements (403 Forbidden)
        AnnouncementRequest req = AnnouncementRequest.builder()
                .title("Unauthorized Student Broadcast")
                .message("Test announcement")
                .build();

        mockMvc.perform(post("/api/v1/notifications/announcements")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());

        // 6. Exam Officer can broadcast announcements (200 OK)
        mockMvc.perform(post("/api/v1/notifications/announcements")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", is("Unauthorized Student Broadcast")));
    }

    @Test
    @DisplayName("Exam Monitoring: Real-time telemetry, snapshot reconciliation, and metrics calculation")
    void testExamMonitoringSnapshotAndTelemetry() throws Exception {
        String studentToken = getAuthToken("student@college.edu");
        String officerToken = getAuthToken("officer@college.edu");

        // Prepare schedule 1
        ExamSchedule schedule = scheduleRepository.findById(1L).orElseThrow();
        schedule.setScheduledStartAt(Instant.now().minus(15, ChronoUnit.MINUTES));
        schedule.setScheduledCloseAt(Instant.now().plus(2, ChronoUnit.HOURS));
        scheduleRepository.save(schedule);

        // 1. Officer fetches schedules list
        mockMvc.perform(get("/api/v1/monitoring/schedules")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())));

        // 2. Initial snapshot before student starts
        mockMvc.perform(get("/api/v1/monitoring/schedules/1/snapshot")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduleId", is(1)))
                .andExpect(jsonPath("$.data.totalEligibleStudents", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.startedCount", is(0)))
                .andExpect(jsonPath("$.data.notStartedCount", greaterThanOrEqualTo(1)));

        // 3. Student starts exam
        MvcResult startResult = mockMvc.perform(post("/api/v1/exam-execution/start")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("scheduleId", "1"))
                .andExpect(status().isCreated())
                .andReturn();

        Number attemptIdNum = com.jayway.jsonpath.JsonPath.read(startResult.getResponse().getContentAsString(), "$.data.attemptId");
        Long attemptId = attemptIdNum.longValue();

        // 4. Student logs telemetry event (TAB_BLUR)
        ExamEventLogRequest eventLog = ExamEventLogRequest.builder()
                .eventType(ExamEventType.TAB_BLUR)
                .payload("Student navigated away from browser tab")
                .build();

        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventLog)))
                .andExpect(status().isOk());

        // 5. Officer fetches snapshot and verifies startedCount=1, activeAttemptsCount=1, and event in timeline
        mockMvc.perform(get("/api/v1/monitoring/schedules/1/snapshot")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startedCount", is(1)))
                .andExpect(jsonPath("$.data.activeAttemptsCount", is(1)))
                .andExpect(jsonPath("$.data.candidates[0].status", is("IN_PROGRESS")))
                .andExpect(jsonPath("$.data.recentEvents[0].eventType", is("TAB_BLUR")));

        // 6. Officer sends real-time announcement to this live exam
        AnnouncementRequest liveAnnounce = AnnouncementRequest.builder()
                .title("Time Warning")
                .message("You have 45 minutes remaining. Ensure all questions are saved.")
                .build();

        mockMvc.perform(post("/api/v1/monitoring/schedules/1/announcement")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(liveAnnounce)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title", is("Time Warning")));

        // 7. Student submits exam
        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // 8. Officer snapshot reflects submittedCount=1, activeAttemptsCount=0
        mockMvc.perform(get("/api/v1/monitoring/schedules/1/snapshot")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startedCount", is(1)))
                .andExpect(jsonPath("$.data.activeAttemptsCount", is(0)))
                .andExpect(jsonPath("$.data.submittedCount", is(1)))
                .andExpect(jsonPath("$.data.candidates[0].status", is("SUBMITTED")));
    }
}
