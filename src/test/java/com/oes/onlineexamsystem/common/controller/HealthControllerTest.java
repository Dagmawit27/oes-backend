package com.oes.onlineexamsystem.common.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.common.dto.ValidationTestRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("GET /api/v1/health should return UP status with database details")
    void testGetHealth_Success() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database.status").value("UP"))
                .andExpect(jsonPath("$.data.database.database").value("oes_db"))
                .andExpect(jsonPath("$.data.database.healthCheckRecords").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("POST /api/v1/health/test-validation with valid data should return 200")
    void testValidation_ValidPayload() throws Exception {
        ValidationTestRequest request = ValidationTestRequest.builder()
                .username("john_doe")
                .email("john@college.edu")
                .age(22)
                .build();

        mockMvc.perform(post("/api/v1/health/test-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("john_doe"))
                .andExpect(jsonPath("$.data.email").value("john@college.edu"));
    }

    @Test
    @DisplayName("POST /api/v1/health/test-validation with invalid data should return 400 with field errors")
    void testValidation_InvalidPayload() throws Exception {
        ValidationTestRequest request = ValidationTestRequest.builder()
                .username("")
                .email("not-an-email")
                .age(15)
                .build();

        mockMvc.perform(post("/api/v1/health/test-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed for one or more fields"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("username", "email", "age")));
    }

    @Test
    @DisplayName("GET /api/v1/health/test-error/not-found should trigger ResourceNotFoundException (404)")
    void testError_NotFound() throws Exception {
        mockMvc.perform(get("/api/v1/health/test-error/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Course not found")));
    }

    @Test
    @DisplayName("GET /api/v1/health/test-error/duplicate should trigger DuplicateResourceException (409)")
    void testError_Duplicate() throws Exception {
        mockMvc.perform(get("/api/v1/health/test-error/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("User already exists")));
    }

    @Test
    @DisplayName("GET /api/v1/health/test-error/business-rule should trigger BusinessRuleException (400)")
    void testError_BusinessRule() throws Exception {
        mockMvc.perform(get("/api/v1/health/test-error/business-rule"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Examination cannot be submitted")));
    }

    @Test
    @DisplayName("GET /api/v1/health/test-error/unauthorized should trigger UnauthorizedException (403)")
    void testError_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/health/test-error/unauthorized"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("You do not have permission")));
    }
}
