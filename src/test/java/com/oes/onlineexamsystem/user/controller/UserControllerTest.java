package com.oes.onlineexamsystem.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.auth.dto.LoginRequest;
import com.oes.onlineexamsystem.user.dto.CreateUserRequest;
import com.oes.onlineexamsystem.user.dto.UpdateUserRequest;
import com.oes.onlineexamsystem.user.entity.RoleName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

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
    @DisplayName("Admin can list users with pagination")
    void testAdmin_ListUsers_Success() throws Exception {
        String adminToken = getAuthToken("admin@college.edu");

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$.data.totalElements", greaterThanOrEqualTo(4)));
    }

    @Test
    @DisplayName("Admin can create a new user with specific role")
    void testAdmin_CreateUser_Success() throws Exception {
        String adminToken = getAuthToken("admin@college.edu");

        String uniqueEmail = "prof.smith." + System.currentTimeMillis() + "@college.edu";
        CreateUserRequest createRequest = CreateUserRequest.builder()
                .email(uniqueEmail)
                .password("Prof@1234")
                .firstName("John")
                .lastName("Smith")
                .phone("+1-555-9988")
                .roles(Set.of(RoleName.ROLE_INSTRUCTOR))
                .build();

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(uniqueEmail))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.roles", hasItem("ROLE_INSTRUCTOR")));
    }

    @Test
    @DisplayName("Admin can update an existing user")
    void testAdmin_UpdateUser_Success() throws Exception {
        String adminToken = getAuthToken("admin@college.edu");

        // Create a user to update
        String uniqueEmail = "update.test." + System.currentTimeMillis() + "@college.edu";
        CreateUserRequest createRequest = CreateUserRequest.builder()
                .email(uniqueEmail)
                .password("Pass@123")
                .firstName("Initial")
                .lastName("Name")
                .roles(Set.of(RoleName.ROLE_STUDENT))
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Number userIdNum = com.jayway.jsonpath.JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");
        Long userId = userIdNum.longValue();

        UpdateUserRequest updateRequest = UpdateUserRequest.builder()
                .firstName("UpdatedFirst")
                .lastName("UpdatedLast")
                .phone("+1-555-7766")
                .isActive(true)
                .roles(Set.of(RoleName.ROLE_STUDENT))
                .build();

        mockMvc.perform(put("/api/v1/users/" + userId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").value("UpdatedFirst"))
                .andExpect(jsonPath("$.data.lastName").value("UpdatedLast"));
    }

    @Test
    @DisplayName("Student accessing /api/v1/users should receive 403 Forbidden")
    void testStudent_AccessUsers_Forbidden() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Instructor accessing /api/v1/users should receive 403 Forbidden")
    void testInstructor_AccessUsers_Forbidden() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Unauthenticated request to /api/v1/users should receive 401 Unauthorized")
    void testUnauthenticated_AccessUsers_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401));
    }
}
