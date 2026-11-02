package com.oes.onlineexamsystem.academic.controller;

import com.oes.onlineexamsystem.academic.dto.BulkImportResultDto;
import com.oes.onlineexamsystem.academic.dto.StudentDto;
import com.oes.onlineexamsystem.academic.service.StudentService;
import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_OFFICER', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<StudentDto>>> getAllStudents() {
        List<StudentDto> students = studentService.getAllStudents();
        return ResponseEntity.ok(ApiResponse.success("Students retrieved successfully", students));
    }

    @GetMapping("/my-profile")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<StudentDto>> getMyStudentProfile(
            @AuthenticationPrincipal UserPrincipal principal) {
        StudentDto student = studentService.getStudentByUserId(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Academic profile retrieved successfully", student));
    }

    @GetMapping("/by-user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EXAM_OFFICER', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<StudentDto>> getStudentByUserId(@PathVariable Long userId) {
        StudentDto student = studentService.getStudentByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Student retrieved successfully", student));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StudentDto>> createStudent(@Valid @RequestBody StudentDto dto) {
        StudentDto created = studentService.createStudent(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student profile created successfully", created));
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BulkImportResultDto>> importStudentsCsv(
            @RequestParam("file") MultipartFile file) {
        BulkImportResultDto result = studentService.importStudentsFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success("Student CSV import processed successfully", result));
    }
}
