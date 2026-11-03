package com.oes.onlineexamsystem.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.academic.repository.StudentRepository;
import com.oes.onlineexamsystem.auth.dto.LoginRequest;
import com.oes.onlineexamsystem.execution.dto.SaveAnswerRequest;
import com.oes.onlineexamsystem.execution.repository.ExamAttemptRepository;
import com.oes.onlineexamsystem.question.entity.QuestionOption;
import com.oes.onlineexamsystem.question.repository.QuestionOptionRepository;
import com.oes.onlineexamsystem.result.dto.ResultCorrectionRequest;
import com.oes.onlineexamsystem.result.dto.SaveGradeItemRequest;
import com.oes.onlineexamsystem.result.dto.SaveGradesRequest;
import com.oes.onlineexamsystem.result.entity.ExamResult;
import com.oes.onlineexamsystem.result.entity.GradingStatus;
import com.oes.onlineexamsystem.result.entity.PublicationStatus;
import com.oes.onlineexamsystem.result.repository.ExamResultRepository;
import com.oes.onlineexamsystem.result.repository.GradeDetailRepository;
import com.oes.onlineexamsystem.result.repository.ResultCorrectionAuditRepository;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GradingAndResultIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamScheduleRepository scheduleRepository;

    @Autowired
    private ExamAttemptRepository attemptRepository;

    @Autowired
    private ExamResultRepository resultRepository;

    @Autowired
    private GradeDetailRepository gradeDetailRepository;

    @Autowired
    private ResultCorrectionAuditRepository correctionAuditRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private QuestionOptionRepository questionOptionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        gradeDetailRepository.deleteAll();
        correctionAuditRepository.deleteAll();
        resultRepository.deleteAll();
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
    @DisplayName("Complete Milestone 10 Lifecycle: Auto-grade, Manual Review, Publish, Student View, and Audited Correction")
    void testCompleteGradingAndResultsWorkflow() throws Exception {
        String studentToken = getAuthToken("student@college.edu");
        String instructorToken = getAuthToken("instructor@college.edu");
        String officerToken = getAuthToken("officer@college.edu");

        // 1. Prepare schedule 1 window to be open now
        ExamSchedule schedule = scheduleRepository.findById(1L).orElseThrow();
        schedule.setScheduledStartAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        schedule.setScheduledCloseAt(Instant.now().plus(2, ChronoUnit.HOURS));
        scheduleRepository.save(schedule);

        // 2. Student starts exam
        MvcResult startResult = mockMvc.perform(post("/api/v1/exam-execution/start")
                        .header("Authorization", "Bearer " + studentToken)
                        .param("scheduleId", "1"))
                .andExpect(status().isCreated())
                .andReturn();

        Number attemptIdNum = com.jayway.jsonpath.JsonPath.read(startResult.getResponse().getContentAsString(), "$.data.attemptId");
        Long attemptId = attemptIdNum.longValue();

        Number examQuestionIdNum = com.jayway.jsonpath.JsonPath.read(startResult.getResponse().getContentAsString(), "$.data.questions[0].examQuestionId");
        Long examQuestionId = examQuestionIdNum.longValue();

        Number optionIdNum = com.jayway.jsonpath.JsonPath.read(startResult.getResponse().getContentAsString(), "$.data.questions[0].options[0].id");
        Long optionId = optionIdNum.longValue();

        // 3. Student answers question and submits
        SaveAnswerRequest saveRequest = SaveAnswerRequest.builder()
                .examQuestionId(examQuestionId)
                .selectedOptionIds(List.of(optionId))
                .isFlagged(false)
                .build();

        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/answers")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saveRequest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/exam-execution/attempts/" + attemptId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // 4. Verify auto-grade generated result and grade details
        List<ExamResult> results = resultRepository.findByExaminationId(schedule.getExamination().getId());
        assertFalse(results.isEmpty(), "ExamResult should be auto-created upon submission");
        ExamResult result = results.get(0);
        assertEquals(PublicationStatus.DRAFT, result.getPublicationStatus());
        assertNotNull(result.getGradingStatus());

        // 5. Student checks results before publication: MUST NOT see unpublished marks!
        mockMvc.perform(get("/api/v1/student/results")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].examinations", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].examinations[?(@.examinationId == " + schedule.getExamination().getId() + ")].displayStatus").value(hasItem("Pending")))
                .andExpect(jsonPath("$.data[0].examinations[?(@.examinationId == " + schedule.getExamination().getId() + ")].earnedMarks").value(hasItem(nullValue())))
                // Strictly NO pass/fail fields
                .andExpect(jsonPath("$.data[0].examinations[0].passed").doesNotExist())
                .andExpect(jsonPath("$.data[0].examinations[0].passFailStatus").doesNotExist());

        // 6. Instructor views exam results roster
        mockMvc.perform(get("/api/v1/instructor/exams/" + schedule.getExamination().getId() + "/results")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].attemptStatus").value("Submitted"));

        // 7. Instructor opens grading canvas for the attempt
        MvcResult gradingResult = mockMvc.perform(get("/api/v1/instructor/attempts/" + attemptId + "/grading")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.attemptId").value(attemptId))
                .andExpect(jsonPath("$.data.questions", hasSize(greaterThanOrEqualTo(1))))
                .andReturn();

        // 8. Validate grading constraints: negative marks rejected
        SaveGradesRequest invalidRequest = SaveGradesRequest.builder()
                .grades(List.of(SaveGradeItemRequest.builder()
                        .examQuestionId(examQuestionId)
                        .awardedMarks(BigDecimal.valueOf(-2.00))
                        .feedback("Negative mark test")
                        .build()))
                .build();

        mockMvc.perform(put("/api/v1/instructor/attempts/" + attemptId + "/grades")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        // 9. Instructor saves valid draft grades
        List<Number> questionIds = com.jayway.jsonpath.JsonPath.read(gradingResult.getResponse().getContentAsString(), "$.data.questions[*].examQuestionId");
        List<SaveGradeItemRequest> gradeItems = questionIds.stream()
                .map(id -> SaveGradeItemRequest.builder()
                        .examQuestionId(id.longValue())
                        .awardedMarks(BigDecimal.valueOf(1.00))
                        .feedback("Good work!")
                        .build())
                .toList();

        SaveGradesRequest validGradesRequest = SaveGradesRequest.builder()
                .grades(gradeItems)
                .build();

        mockMvc.perform(put("/api/v1/instructor/attempts/" + attemptId + "/grades")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validGradesRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 10. Instructor completes grading
        mockMvc.perform(post("/api/v1/instructor/attempts/" + attemptId + "/complete-grading")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validGradesRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gradingStatus").value("COMPLETED"));

        // 11. Exam Officer publishes result
        mockMvc.perform(post("/api/v1/exam-officer/results/" + result.getId() + "/publish")
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 12. Student views published results: now visible with formatted marks
        mockMvc.perform(get("/api/v1/student/results")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].examinations[?(@.examinationId == " + schedule.getExamination().getId() + ")].displayStatus").value(hasItem("Published")))
                .andExpect(jsonPath("$.data[0].examinations[?(@.examinationId == " + schedule.getExamination().getId() + ")].earnedMarks").value(hasItem(6.0)))
                .andExpect(jsonPath("$.data[0].examinations[?(@.examinationId == " + schedule.getExamination().getId() + ")].marksDisplay").value(hasItem(containsString("6.00 / "))))
                // Strictly verify no pass/fail
                .andExpect(jsonPath("$.data[0].examinations[0].passed").doesNotExist());

        // 13. Audited Correction by Instructor / Officer
        ResultCorrectionRequest correctionRequest = ResultCorrectionRequest.builder()
                .newEarnedMarks(BigDecimal.valueOf(6.50))
                .reason("Re-evaluation of partial credit upon student inquiry")
                .build();

        mockMvc.perform(post("/api/v1/instructor/results/" + result.getId() + "/correct")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctionRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ExamResult updatedResult = resultRepository.findById(result.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("6.50").compareTo(updatedResult.getEarnedMarks()));

        // 14. Verify CSV export works cleanly
        mockMvc.perform(get("/api/v1/results/export")
                        .header("Authorization", "Bearer " + instructorToken)
                        .param("examId", schedule.getExamination().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(content().string(containsString("Student ID,Student Name,Course,Examination")));
    }
}
