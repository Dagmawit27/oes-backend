package com.oes.onlineexamsystem.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.academic.entity.Student;
import com.oes.onlineexamsystem.academic.repository.StudentRepository;
import com.oes.onlineexamsystem.auth.dto.LoginRequest;
import com.oes.onlineexamsystem.execution.dto.ExamEventLogRequest;
import com.oes.onlineexamsystem.execution.dto.SaveAnswerRequest;
import com.oes.onlineexamsystem.execution.entity.ExamEventType;
import com.oes.onlineexamsystem.execution.repository.ExamAttemptRepository;
import com.oes.onlineexamsystem.question.entity.QuestionOption;
import com.oes.onlineexamsystem.question.repository.QuestionOptionRepository;
import com.oes.onlineexamsystem.schedule.entity.ExamSchedule;
import com.oes.onlineexamsystem.schedule.repository.ExamScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ExamExecutionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamScheduleRepository scheduleRepository;

    @Autowired
    private ExamAttemptRepository attemptRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        attemptRepository.deleteAll();
    }

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
    @DisplayName("Student can start exam, save answers, flag questions, and submit attempt safely")
    void testCompleteExamExecutionFlow() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        // Prepare schedule 1 window to be open now for testing
        ExamSchedule schedule = scheduleRepository.findById(1L).orElseThrow();
        schedule.setScheduledStartAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        schedule.setScheduledCloseAt(Instant.now().plus(2, ChronoUnit.HOURS));
        scheduleRepository.save(schedule);

        // 1. Start Exam: Student initializes attempt
        MvcResult startResult = mockMvc.perform(post("/api/v1/exam-execution/start")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("scheduleId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.attemptId").isNumber())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.questions", hasSize(greaterThanOrEqualTo(1))))
                // CRUCIAL SECURITY: ensure no option leaks correct answer or explanations!
                .andExpect(jsonPath("$.data.questions[0].options[0].isCorrect").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].options[0].explanation").doesNotExist())
                .andReturn();

        Number attemptIdNum = com.jayway.jsonpath.JsonPath.read(startResult.getResponse().getContentAsString(), "$.data.attemptId");
        Long attemptId = attemptIdNum.longValue();

        Number examQuestionIdNum = com.jayway.jsonpath.JsonPath.read(startResult.getResponse().getContentAsString(), "$.data.questions[0].examQuestionId");
        Long examQuestionId = examQuestionIdNum.longValue();

        Number optionIdNum = com.jayway.jsonpath.JsonPath.read(startResult.getResponse().getContentAsString(), "$.data.questions[0].options[0].id");
        Long optionId = optionIdNum.longValue();

        // 2. Idempotent Start: Calling start again returns the active attempt with existing state
        mockMvc.perform(post("/api/v1/exam-execution/start")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("scheduleId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.attemptId").value(attemptId))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        // 3. Save Answer: Student selects option and flags question
        SaveAnswerRequest saveRequest = SaveAnswerRequest.builder()
                .examQuestionId(examQuestionId)
                .selectedOptionIds(List.of(optionId))
                .isFlagged(true)
                .build();

        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.examQuestionId").value(examQuestionId))
                .andExpect(jsonPath("$.data.isFlagged").value(true))
                .andExpect(jsonPath("$.data.selectedOptionIds[0]").value(optionId));

        // 4. Log client-side telemetry event (e.g. Tab switch or window resize)
        ExamEventLogRequest eventRequest = ExamEventLogRequest.builder()
                .eventType(ExamEventType.TAB_BLUR)
                .payload("User switched browser tab")
                .build();

        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/events")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventRequest)))
                .andExpect(status().isOk());

        // 5. Submit Exam: Finalize attempt
        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.answeredQuestions", greaterThanOrEqualTo(1)));

        // 6. Idempotent Submission: Subsequent submit call returns summary without error
        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // 7. Modifying answer after submission fails with 400 Bad Request
        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Answers cannot be saved")));
    }

    @Test
    @DisplayName("Unauthorized attempt access: Student cannot save answers to another student's attempt")
    void testAttemptOwnership_Enforced() throws Exception {
        String studentToken = getAuthToken("student@college.edu");
        String adminToken = getAuthToken("admin@college.edu");

        // Prepare schedule 1
        ExamSchedule schedule = scheduleRepository.findById(1L).orElseThrow();
        schedule.setScheduledStartAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        schedule.setScheduledCloseAt(Instant.now().plus(2, ChronoUnit.HOURS));
        scheduleRepository.save(schedule);

        // Student creates or gets active attempt
        MvcResult startResult = mockMvc.perform(post("/api/v1/exam-execution/start")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("scheduleId", "1"))
                .andReturn();

        Number attemptIdNum = com.jayway.jsonpath.JsonPath.read(startResult.getResponse().getContentAsString(), "$.data.attemptId");
        Long attemptId = attemptIdNum.longValue();

        // Admin or another user attempting to modify student's attempt will be rejected
        SaveAnswerRequest saveRequest = SaveAnswerRequest.builder()
                .examQuestionId(1L)
                .answerText("Hacked answer")
                .build();

        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Invalid option selection from different question fails validation")
    void testSaveAnswer_InvalidOption_Fails() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        // Prepare schedule 1
        ExamSchedule schedule = scheduleRepository.findById(1L).orElseThrow();
        schedule.setScheduledStartAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        schedule.setScheduledCloseAt(Instant.now().plus(2, ChronoUnit.HOURS));
        scheduleRepository.save(schedule);

        MvcResult startResult = mockMvc.perform(post("/api/v1/exam-execution/start")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("scheduleId", "1"))
                .andReturn();

        Number attemptIdNum = com.jayway.jsonpath.JsonPath.read(startResult.getResponse().getContentAsString(), "$.data.attemptId");
        Long attemptId = attemptIdNum.longValue();

        // Option 10 belongs to Question 2, not Question 1
        SaveAnswerRequest badRequest = SaveAnswerRequest.builder()
                .examQuestionId(1L)
                .selectedOptionIds(List.of(10L)) // Mismatched option
                .build();

        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest());
    }
}
