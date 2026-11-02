package com.oes.onlineexamsystem.academic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oes.onlineexamsystem.academic.dto.CourseDto;
import com.oes.onlineexamsystem.academic.dto.DepartmentDto;
import com.oes.onlineexamsystem.academic.dto.EnrollmentDto;
import com.oes.onlineexamsystem.auth.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AcademicIntegrationTest {

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
    @DisplayName("Authenticated users can list departments")
    void testGetDepartments_Success() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        mockMvc.perform(get("/api/v1/departments")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[*].code", hasItem("CSIT")));
    }

    @Test
    @DisplayName("Admin can create a new department")
    void testAdmin_CreateDepartment_Success() throws Exception {
        String adminToken = getAuthToken("admin@college.edu");

        String code = "PHY" + System.currentTimeMillis() % 1000;
        DepartmentDto deptDto = DepartmentDto.builder()
                .name("Physics Department " + code)
                .code(code)
                .description("Department of Physics & Astronomy")
                .build();

        mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value(code));
    }

    @Test
    @DisplayName("Non-admin cannot create department (403 Forbidden)")
    void testStudent_CreateDepartment_Forbidden() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        DepartmentDto deptDto = DepartmentDto.builder()
                .name("Unauthorized Dept")
                .code("UNAUTH")
                .build();

        mockMvc.perform(post("/api/v1/departments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deptDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("Student can view their enrolled courses via /api/v1/enrollments/my-courses")
    void testStudent_ViewEnrolledCourses_Success() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        mockMvc.perform(get("/api/v1/enrollments/my-courses")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].courseCode").value("CS101"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("Prevent duplicate enrollment for same student and course offering (409 Conflict)")
    void testEnrollment_PreventDuplicate_Conflict() throws Exception {
        String adminToken = getAuthToken("admin@college.edu");

        EnrollmentDto duplicateEnrollment = EnrollmentDto.builder()
                .studentId(1L)
                .courseOfferingId(1L)
                .build();

        mockMvc.perform(post("/api/v1/enrollments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateEnrollment)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already enrolled")));
    }

    @Test
    @DisplayName("Admin can bulk import students via CSV with validation and summary")
    void testAdmin_BulkImportStudentsCsv_Success() throws Exception {
        String adminToken = getAuthToken("admin@college.edu");
        long ts = System.currentTimeMillis() % 10000;

        String csvContent = "registrationNumber,firstName,lastName,email,programCode,yearOfStudy\n" +
                "STU-" + ts + "-1,BulkAlice,Smith,alice." + ts + "@college.edu,BSCS,1\n" +
                "STU-" + ts + "-2,BulkBob,Taylor,bob." + ts + "@college.edu,BSCS,2\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "students.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/students/import-csv")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRecords").value(2))
                .andExpect(jsonPath("$.data.successfulRecords").value(2))
                .andExpect(jsonPath("$.data.failedRecords").value(0))
                .andExpect(jsonPath("$.data.errors", hasSize(0)));
    }

    @Test
    @DisplayName("Bulk import CSV detects duplicates and invalid program codes in error summary")
    void testAdmin_BulkImportStudentsCsv_WithErrors() throws Exception {
        String adminToken = getAuthToken("admin@college.edu");

        String csvContent = "registrationNumber,firstName,lastName,email,programCode,yearOfStudy\n" +
                "STU-2025-001,DupStudent,One,dup1@college.edu,BSCS,1\n" +
                "STU-ERR-02,InvalidProg,Two,invprog@college.edu,NON_EXISTING_PROG,1\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "students_with_errors.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/students/import-csv")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRecords").value(2))
                .andExpect(jsonPath("$.data.successfulRecords").value(0))
                .andExpect(jsonPath("$.data.failedRecords").value(2))
                .andExpect(jsonPath("$.data.errors", hasSize(2)));
    }

    @Test
    @DisplayName("Instructor can view enrolled student roster for their course offering")
    void testInstructor_ViewOfferingStudents_Success() throws Exception {
        String instructorToken = getAuthToken("instructor@college.edu");

        mockMvc.perform(get("/api/v1/course-offerings/1/students")
                        .header("Authorization", "Bearer " + instructorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].studentRegistrationNumber").value("STU-2025-001"));
    }

    @Test
    @DisplayName("Student can view their own academic profile")
    void testStudent_ViewMyAcademicProfile_Success() throws Exception {
        String studentToken = getAuthToken("student@college.edu");

        mockMvc.perform(get("/api/v1/students/my-profile")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.registrationNumber").value("STU-2025-001"))
                .andExpect(jsonPath("$.data.programCode").value("BSCS"));
    }
}
