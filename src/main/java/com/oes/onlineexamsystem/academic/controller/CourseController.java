package com.oes.onlineexamsystem.academic.controller;

import com.oes.onlineexamsystem.academic.dto.CourseDto;
import com.oes.onlineexamsystem.academic.service.CourseService;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseDto>>> getAllCourses() {
        List<CourseDto> courses = courseService.getAllCourses();
        return ResponseEntity.ok(ApiResponse.success("Courses retrieved successfully", courses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseDto>> getCourseById(@PathVariable Long id) {
        CourseDto course = courseService.getCourseById(id);
        return ResponseEntity.ok(ApiResponse.success("Course retrieved successfully", course));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseDto>> createCourse(@Valid @RequestBody CourseDto dto) {
        CourseDto created = courseService.createCourse(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created successfully", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CourseDto>> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody CourseDto dto) {
        CourseDto updated = courseService.updateCourse(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Course updated successfully", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(@PathVariable Long id) {
        courseService.deactivateCourse(id);
        return ResponseEntity.ok(ApiResponse.success("Course deactivated successfully", null));
    }
}
