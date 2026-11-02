package com.oes.onlineexamsystem.academic.controller;

import com.oes.onlineexamsystem.academic.dto.EnrollmentDto;
import com.oes.onlineexamsystem.academic.service.EnrollmentService;
import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<EnrollmentDto>>> getAllEnrollments(
            @RequestParam(required = false) Long courseOfferingId) {
        List<EnrollmentDto> enrollments = courseOfferingId != null
                ? enrollmentService.getEnrollmentsByOffering(courseOfferingId)
                : enrollmentService.getAllEnrollments();
        return ResponseEntity.ok(ApiResponse.success("Enrollments retrieved successfully", enrollments));
    }

    @GetMapping("/my-courses")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApiResponse<List<EnrollmentDto>>> getMyEnrolledCourses(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<EnrollmentDto> enrollments = enrollmentService.getEnrollmentsByStudentUserId(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Enrolled courses retrieved successfully", enrollments));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<EnrollmentDto>> enrollStudent(@Valid @RequestBody EnrollmentDto dto) {
        EnrollmentDto enrolled = enrollmentService.enrollStudent(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Student enrolled successfully", enrolled));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> dropEnrollment(@PathVariable Long id) {
        enrollmentService.dropEnrollment(id);
        return ResponseEntity.ok(ApiResponse.success("Enrollment dropped successfully", null));
    }
}
