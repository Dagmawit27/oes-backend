package com.oes.onlineexamsystem.academic.controller;

import com.oes.onlineexamsystem.academic.dto.CourseOfferingDto;
import com.oes.onlineexamsystem.academic.dto.EnrollmentDto;
import com.oes.onlineexamsystem.academic.service.CourseOfferingService;
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
@RequestMapping("/api/v1/course-offerings")
public class CourseOfferingController {

    private final CourseOfferingService courseOfferingService;
    private final EnrollmentService enrollmentService;

    public CourseOfferingController(CourseOfferingService courseOfferingService,
                                    EnrollmentService enrollmentService) {
        this.courseOfferingService = courseOfferingService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseOfferingDto>>> getAllOfferings(
            @RequestParam(required = false) Long semesterId) {
        List<CourseOfferingDto> offerings = semesterId != null
                ? courseOfferingService.getOfferingsBySemester(semesterId)
                : courseOfferingService.getAllOfferings();
        return ResponseEntity.ok(ApiResponse.success("Course offerings retrieved successfully", offerings));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseOfferingDto>> getOfferingById(@PathVariable Long id) {
        CourseOfferingDto offering = courseOfferingService.getOfferingById(id);
        return ResponseEntity.ok(ApiResponse.success("Course offering retrieved successfully", offering));
    }

    @GetMapping("/{id}/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'EXAM_OFFICER')")
    public ResponseEntity<ApiResponse<List<EnrollmentDto>>> getEnrolledStudents(@PathVariable Long id) {
        List<EnrollmentDto> students = enrollmentService.getEnrollmentsByOffering(id);
        return ResponseEntity.ok(ApiResponse.success("Enrolled students retrieved successfully", students));
    }

    @GetMapping("/my-assigned")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<CourseOfferingDto>>> getMyAssignedOfferings(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<CourseOfferingDto> offerings = courseOfferingService.getOfferingsByInstructorUserId(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Assigned offerings retrieved successfully", offerings));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseOfferingDto>> createOffering(
            @Valid @RequestBody CourseOfferingDto dto) {
        CourseOfferingDto created = courseOfferingService.createOffering(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course offering created successfully", created));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateOffering(@PathVariable Long id) {
        courseOfferingService.deactivateOffering(id);
        return ResponseEntity.ok(ApiResponse.success("Course offering deactivated successfully", null));
    }
}
