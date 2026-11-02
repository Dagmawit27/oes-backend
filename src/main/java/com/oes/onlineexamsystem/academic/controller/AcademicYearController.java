package com.oes.onlineexamsystem.academic.controller;

import com.oes.onlineexamsystem.academic.dto.AcademicYearDto;
import com.oes.onlineexamsystem.academic.dto.SemesterDto;
import com.oes.onlineexamsystem.academic.service.AcademicYearService;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/academic-years")
public class AcademicYearController {

    private final AcademicYearService academicYearService;

    public AcademicYearController(AcademicYearService academicYearService) {
        this.academicYearService = academicYearService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AcademicYearDto>>> getAllAcademicYears() {
        List<AcademicYearDto> years = academicYearService.getAllAcademicYears();
        return ResponseEntity.ok(ApiResponse.success("Academic years retrieved successfully", years));
    }

    @GetMapping("/semesters")
    public ResponseEntity<ApiResponse<List<SemesterDto>>> getAllSemesters() {
        List<SemesterDto> semesters = academicYearService.getAllSemesters();
        return ResponseEntity.ok(ApiResponse.success("All semesters retrieved successfully", semesters));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AcademicYearDto>> createAcademicYear(@Valid @RequestBody AcademicYearDto dto) {
        AcademicYearDto created = academicYearService.createAcademicYear(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Academic year created successfully", created));
    }

    @GetMapping("/{id}/semesters")
    public ResponseEntity<ApiResponse<List<SemesterDto>>> getSemestersByAcademicYear(@PathVariable Long id) {
        List<SemesterDto> semesters = academicYearService.getSemestersByAcademicYear(id);
        return ResponseEntity.ok(ApiResponse.success("Semesters retrieved successfully", semesters));
    }

    @PostMapping("/{id}/semesters")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SemesterDto>> createSemester(
            @PathVariable Long id,
            @Valid @RequestBody SemesterDto dto) {
        dto.setAcademicYearId(id);
        SemesterDto created = academicYearService.createSemester(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Semester created successfully", created));
    }
}
