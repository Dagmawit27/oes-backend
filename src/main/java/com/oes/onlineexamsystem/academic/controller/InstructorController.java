package com.oes.onlineexamsystem.academic.controller;

import com.oes.onlineexamsystem.academic.dto.InstructorDto;
import com.oes.onlineexamsystem.academic.service.InstructorService;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/instructors")
public class InstructorController {

    private final InstructorService instructorService;

    public InstructorController(InstructorService instructorService) {
        this.instructorService = instructorService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InstructorDto>>> getAllInstructors() {
        List<InstructorDto> instructors = instructorService.getAllInstructors();
        return ResponseEntity.ok(ApiResponse.success("Instructors retrieved successfully", instructors));
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<ApiResponse<InstructorDto>> getInstructorByUserId(@PathVariable Long userId) {
        InstructorDto instructor = instructorService.getInstructorByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Instructor retrieved successfully", instructor));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InstructorDto>> createInstructor(@Valid @RequestBody InstructorDto dto) {
        InstructorDto created = instructorService.createInstructor(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Instructor profile created successfully", created));
    }
}
