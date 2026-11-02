package com.oes.onlineexamsystem.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.auth.dto.ChangePasswordRequest;
import com.oes.onlineexamsystem.auth.dto.LoginRequest;
import com.oes.onlineexamsystem.auth.dto.RefreshTokenRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest(name = "Login should succeed for {0} with role {1}")
    @CsvSource({
            "admin@college.edu, ROLE_ADMIN",
            "officer@college.edu, ROLE_EXAM_OFFICER",
            "instructor@college.edu, ROLE_INSTRUCTOR",
            "student@college.edu, ROLE_STUDENT"
    })
    @DisplayName("Verify authentication and JWT generation for all 4 primary roles")
    void testLogin_AllRoles_Success(String email, String expectedRole) throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email(email)
                .password("Admin@123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.roles", hasItem(expectedRole)))
                .andExpect(cookie().exists("access_token"))
                .andExpect(cookie().exists("refresh_token"));
    }

    @Test
    @DisplayName("Login with invalid password should return 401 Unauthorized")
    void testLogin_InvalidCredentials() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("admin@college.edu")
                .password("WrongPassword999")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Invalid email or password")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me with valid Bearer token should return user profile")
    void testGetCurrentUser_WithToken() throws Exception {
        // First login as student
        LoginRequest loginRequest = LoginRequest.builder()
                .email("student@college.edu")
                .password("Admin@123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(responseBody, "$.data.accessToken");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("student@college.edu"))
                .andExpect(jsonPath("$.data.roles", hasItem("ROLE_STUDENT")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh should issue a new access token")
    void testRefreshToken_Success() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("instructor@college.edu")
                .password("Admin@123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String refreshToken = com.jayway.jsonpath.JsonPath.read(responseBody, "$.data.refreshToken");

        RefreshTokenRequest refreshRequest = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.email").value("instructor@college.edu"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout should revoke refresh token")
    void testLogout_Success() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .email("officer@college.edu")
                .password("Admin@123")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String refreshToken = com.jayway.jsonpath.JsonPath.read(responseBody, "$.data.refreshToken");

        RefreshTokenRequest logoutRequest = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Attempting to refresh with the revoked token must now fail with 403 / 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isForbidden());
    }
}
