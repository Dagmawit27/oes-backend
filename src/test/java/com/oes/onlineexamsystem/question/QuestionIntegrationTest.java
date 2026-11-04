package com.oes.onlineexamsystem.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.auth.dto.LoginRequest;
import com.oes.onlineexamsystem.question.dto.CreateQuestionRequest;
import com.oes.onlineexamsystem.question.dto.QuestionOptionDto;
import com.oes.onlineexamsystem.question.entity.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class QuestionIntegrationTest {

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
    @DisplayName("Instructor can list questions with filtering")
    void testGetQuestions_Success() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        mockMvc.perform(get("/api/v1/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .param("type", "SINGLE_CHOICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].questionType").value("SINGLE_CHOICE"));
    }

    @Test
    @DisplayName("Instructor can create a SINGLE_CHOICE question with exactly one correct option")
    void testCreateSingleChoiceQuestion_Success() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        CreateQuestionRequest request = CreateQuestionRequest.builder()
                .courseId(1L)
                .questionText("Which protocol operates at the Transport Layer of the OSI model?")
                .questionType(QuestionType.SINGLE_CHOICE)
                .defaultMarks(BigDecimal.valueOf(2.00))
                .explanation("TCP and UDP operate at Layer 4 (Transport Layer).")
                .options(List.of(
                        QuestionOptionDto.builder().optionText("TCP").optionOrder(1).isCorrect(true).build(),
                        QuestionOptionDto.builder().optionText("HTTP").optionOrder(2).isCorrect(false).build(),
                        QuestionOptionDto.builder().optionText("IP").optionOrder(3).isCorrect(false).build(),
                        QuestionOptionDto.builder().optionText("Ethernet").optionOrder(4).isCorrect(false).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionText").value(request.getQuestionText()))
                .andExpect(jsonPath("$.data.options", hasSize(4)));
    }

    @Test
    @DisplayName("Single choice question with invalid correct option count fails validation (400 Bad Request)")
    void testCreateSingleChoiceQuestion_InvalidCorrectCount_Fails() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        // 2 options marked correct instead of exactly 1
        CreateQuestionRequest request = CreateQuestionRequest.builder()
                .courseId(1L)
                .questionText("Invalid Single Choice Question")
                .questionType(QuestionType.SINGLE_CHOICE)
                .defaultMarks(BigDecimal.valueOf(1.00))
                .options(List.of(
                        QuestionOptionDto.builder().optionText("Option 1").optionOrder(1).isCorrect(true).build(),
                        QuestionOptionDto.builder().optionText("Option 2").optionOrder(2).isCorrect(true).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("exactly one correct option")));
    }

    @Test
    @DisplayName("Instructor can create MULTIPLE_CHOICE question with multiple correct options")
    void testCreateMultipleChoiceQuestion_Success() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        CreateQuestionRequest request = CreateQuestionRequest.builder()
                .courseId(1L)
                .questionText("Which of the following are non-linear data structures? (Select all that apply)")
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .defaultMarks(BigDecimal.valueOf(3.00))
                .options(List.of(
                        QuestionOptionDto.builder().optionText("Tree").optionOrder(1).isCorrect(true).build(),
                        QuestionOptionDto.builder().optionText("Graph").optionOrder(2).isCorrect(true).build(),
                        QuestionOptionDto.builder().optionText("Array").optionOrder(3).isCorrect(false).build(),
                        QuestionOptionDto.builder().optionText("Stack").optionOrder(4).isCorrect(false).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionType").value("MULTIPLE_CHOICE"));
    }

    @Test
    @DisplayName("Instructor can create TRUE_FALSE question")
    void testCreateTrueFalseQuestion_Success() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        CreateQuestionRequest request = CreateQuestionRequest.builder()
                .courseId(1L)
                .questionText("In Java, arrays have a fixed size after instantiation.")
                .questionType(QuestionType.TRUE_FALSE)
                .defaultMarks(BigDecimal.valueOf(1.00))
                .options(List.of(
                        QuestionOptionDto.builder().optionText("True").optionOrder(1).isCorrect(true).build(),
                        QuestionOptionDto.builder().optionText("False").optionOrder(2).isCorrect(false).build()
                ))
                .build();

        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Instructor can create ESSAY question without predefined options")
    void testCreateEssayQuestion_Success() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        CreateQuestionRequest request = CreateQuestionRequest.builder()
                .courseId(1L)
                .questionText("Describe the four ACID properties in database management systems.")
                .questionType(QuestionType.ESSAY)
                .defaultMarks(BigDecimal.valueOf(8.00))
                .explanation("Grading Criteria: Atomicity (2), Consistency (2), Isolation (2), Durability (2).")
                .options(List.of())
                .build();

        mockMvc.perform(post("/api/v1/questions")
                        .header("Authorization", "Bearer " + instructorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.options", hasSize(0)));
    }

    @Test
    @DisplayName("CRITICAL SECURITY: Student endpoint /student-view strips answer keys and private explanations")
    void testStudentQuestionView_StripsAnswerKeys_SecurityEnforced() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        // Seeded question ID 1 is a SINGLE_CHOICE question
        mockMvc.perform(get("/api/v1/questions/1/student-view")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionText", notNullValue()))
                .andExpect(jsonPath("$.data.explanation").doesNotExist()) // Must NOT expose explanation
                .andExpect(jsonPath("$.data.options", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data.options[*].isCorrect").doesNotExist()); // Must NEVER expose isCorrect
    }

    @Test
    @DisplayName("Instructor can duplicate a question for reuse across exams")
    void testDuplicateQuestion_Success() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        mockMvc.perform(post("/api/v1/questions/1/duplicate")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionText", containsString("(Copy)")))
                .andExpect(jsonPath("$.data.version").value(1));
    }
}
