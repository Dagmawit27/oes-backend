package com.oes.onlineexamsystem.academic.controller;

import com.oes.onlineexamsystem.academic.dto.ProgramDto;
import com.oes.onlineexamsystem.academic.service.ProgramService;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/programs")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProgramDto>>> getAllPrograms() {
        List<ProgramDto> programs = programService.getAllPrograms();
        return ResponseEntity.ok(ApiResponse.success("Programs retrieved successfully", programs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProgramDto>> getProgramById(@PathVariable Long id) {
        ProgramDto program = programService.getProgramById(id);
        return ResponseEntity.ok(ApiResponse.success("Program retrieved successfully", program));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProgramDto>> createProgram(@Valid @RequestBody ProgramDto dto) {
        ProgramDto created = programService.createProgram(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Program created successfully", created));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProgram(@PathVariable Long id) {
        programService.deactivateProgram(id);
        return ResponseEntity.ok(ApiResponse.success("Program deactivated successfully", null));
    }
}
