package com.oes.onlineexamsystem.examination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.auth.dto.LoginRequest;
import com.oes.onlineexamsystem.examination.dto.CreateExaminationRequest;
import com.oes.onlineexamsystem.examination.dto.ReviewExaminationRequest;
import com.oes.onlineexamsystem.examination.dto.SelectedQuestionRequest;
import com.oes.onlineexamsystem.examination.dto.UpdateExaminationRequest;
import com.oes.onlineexamsystem.examination.entity.ApprovalDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ExamIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    @DisplayName("Instructor can view examinations list")
    void testGetExaminations_Success() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        mockMvc.perform(get("/api/v1/examinations")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Instructor can create an examination draft with selected questions")
    void testCreateExaminationDraft_Success() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        CreateExaminationRequest request = CreateExaminationRequest.builder()
                .title("CS101 Final Exam - Automated Test " + System.currentTimeMillis())
                .description("Automated test final exam")
                .instructions("Answer all questions thoroughly.")
                .courseOfferingId(1L)
                .durationMinutes(90)
                .totalMarks(BigDecimal.valueOf(5.00))
                .passingMarks(BigDecimal.valueOf(2.50))
                .randomizeQuestions(true)
                .randomizeOptions(true)
                .attemptLimit(1)
                .negativeMarkingEnabled(false)
                .questions(List.of(
                        SelectedQuestionRequest.builder().questionId(1L).questionOrder(1).marks(BigDecimal.valueOf(2.00)).build(),
                        SelectedQuestionRequest.builder().questionId(2L).questionOrder(2).marks(BigDecimal.valueOf(3.00)).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/examinations")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value(request.getTitle()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalMarks").value(5.0))
                .andExpect(jsonPath("$.data.questions", hasSize(2)));
    }

    @Test
    @DisplayName("Creating examination with mismatched total marks fails validation (400 Bad Request)")
    void testCreateExamination_MismatchedMarks_Fails() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        CreateExaminationRequest request = CreateExaminationRequest.builder()
                .title("CS101 Bad Marks Exam " + System.currentTimeMillis())
                .courseOfferingId(1L)
                .durationMinutes(60)
                .totalMarks(BigDecimal.valueOf(10.00)) // Specified 10.00, but sum of questions is 5.00
                .passingMarks(BigDecimal.valueOf(5.00))
                .questions(List.of(
                        SelectedQuestionRequest.builder().questionId(1L).questionOrder(1).marks(BigDecimal.valueOf(2.00)).build(),
                        SelectedQuestionRequest.builder().questionId(2L).questionOrder(2).marks(BigDecimal.valueOf(3.00)).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/examinations")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("does not match sum")));
    }

    @Test
    @DisplayName("Complete Examination Lifecycle: Draft -> Submit -> Review Reject -> Revise -> Submit -> Review Approve with Snapshot")
    void testExaminationLifecycle_FullFlow() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");
        String officerToken = getAuthToken("officer@college.edu");

        // Step 1: Instructor creates Draft
        CreateExaminationRequest createRequest = CreateExaminationRequest.builder()
                .title("Lifecycle Exam " + System.currentTimeMillis())
                .description("Testing complete lifecycle")
                .instructions("Complete in 45 minutes")
                .courseOfferingId(1L)
                .durationMinutes(45)
                .totalMarks(BigDecimal.valueOf(4.00))
                .passingMarks(BigDecimal.valueOf(2.00))
                .questions(List.of(
                        SelectedQuestionRequest.builder().questionId(1L).questionOrder(1).marks(BigDecimal.valueOf(2.00)).build(),
                        SelectedQuestionRequest.builder().questionId(3L).questionOrder(2).marks(BigDecimal.valueOf(2.00)).build()
                ))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/examinations")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();

        Number examIdNum = com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");
        Long examId = examIdNum.longValue();

        // Step 2: Instructor submits draft for approval
        mockMvc.perform(post("/api/v1/examinations/" + examId + "/submit")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // Step 3: Exam Officer attempts rejection without comments -> Fails
        ReviewExaminationRequest rejectNoComments = ReviewExaminationRequest.builder()
                .decision(ApprovalDecision.REJECTED)
                .comments("   ")
                .build();

        mockMvc.perform(post("/api/v1/examinations/" + examId + "/review")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectNoComments)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Mandatory reviewer feedback comments")));

        // Step 4: Exam Officer rejects with comments
        ReviewExaminationRequest rejectWithComments = ReviewExaminationRequest.builder()
                .decision(ApprovalDecision.REJECTED)
                .comments("Please add a short essay question to better evaluate depth.")
                .build();

        mockMvc.perform(post("/api/v1/examinations/" + examId + "/review")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectWithComments)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        // Step 5: Instructor modifies rejected examination (adds Question 5 for 5 marks, total 7)
        UpdateExaminationRequest updateRequest = UpdateExaminationRequest.builder()
                .title("Lifecycle Exam Revised " + System.currentTimeMillis())
                .description("Updated with essay question")
                .instructions("Complete in 60 minutes")
                .durationMinutes(60)
                .totalMarks(BigDecimal.valueOf(7.00))
                .passingMarks(BigDecimal.valueOf(3.50))
                .questions(List.of(
                        SelectedQuestionRequest.builder().questionId(1L).questionOrder(1).marks(BigDecimal.valueOf(2.00)).build(),
                        SelectedQuestionRequest.builder().questionId(5L).questionOrder(2).marks(BigDecimal.valueOf(5.00)).build()
                ))
                .build();

        mockMvc.perform(put("/api/v1/examinations/" + examId)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMarks").value(7.0));

        // Step 6: Instructor resubmits for approval
        mockMvc.perform(post("/api/v1/examinations/" + examId + "/submit")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // Step 7: Exam Officer approves examination
        ReviewExaminationRequest approveRequest = ReviewExaminationRequest.builder()
                .decision(ApprovalDecision.APPROVED)
                .comments("Revisions verified. Approved for scheduling.")
                .build();

        mockMvc.perform(post("/api/v1/examinations/" + examId + "/review")
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.questions[0].questionSnapshot", notNullValue()));

        // Step 8: Verifying APPROVED examination cannot be modified
        mockMvc.perform(put("/api/v1/examinations/" + examId)
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot modify examination in APPROVED status")));

        // Step 9: Verify approval history log
        mockMvc.perform(get("/api/v1/examinations/" + examId + "/approvals")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2))) // 1 rejection + 1 approval
                .andExpect(jsonPath("$.data[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$.data[1].decision").value("REJECTED"));
    }

    @Test
    @DisplayName("Student cannot create or review examinations (403 Forbidden)")
    void testStudentAccess_Forbidden() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        CreateExaminationRequest request = CreateExaminationRequest.builder()
                .title("Unauthorized Student Exam")
                .courseOfferingId(1L)
                .durationMinutes(30)
                .totalMarks(BigDecimal.valueOf(2.00))
                .passingMarks(BigDecimal.valueOf(1.00))
                .questions(List.of(
                        SelectedQuestionRequest.builder().questionId(1L).questionOrder(1).marks(BigDecimal.valueOf(2.00)).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/examinations")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        ReviewExaminationRequest reviewRequest = ReviewExaminationRequest.builder()
                .decision(ApprovalDecision.APPROVED)
                .comments("Student hacking approval")
                .build();

        mockMvc.perform(post("/api/v1/examinations/1/review")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isForbidden());
    }
}
