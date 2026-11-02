package com.oes.onlineexamsystem.question.controller;

import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.question.dto.QuestionBankDto;
import com.oes.onlineexamsystem.question.service.QuestionBankService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/question-banks")
public class QuestionBankController {

    private final QuestionBankService bankService;

    public QuestionBankController(QuestionBankService bankService) {
        this.bankService = bankService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionBankDto>>> getAllBanks(
            @RequestParam(required = false) Long courseId) {
        List<QuestionBankDto> banks = courseId != null
                ? bankService.getBanksByCourse(courseId)
                : bankService.getAllBanks();
        return ResponseEntity.ok(ApiResponse.success("Question banks retrieved successfully", banks));
    }

    @GetMapping("/my-banks")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<QuestionBankDto>>> getMyBanks(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<QuestionBankDto> banks = bankService.getBanksByInstructor(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Assigned question banks retrieved successfully", banks));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionBankDto>> getBankById(@PathVariable Long id) {
        QuestionBankDto bank = bankService.getBankById(id);
        return ResponseEntity.ok(ApiResponse.success("Question bank retrieved successfully", bank));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<QuestionBankDto>> createBank(
            @Valid @RequestBody QuestionBankDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        QuestionBankDto created = bankService.createBank(dto, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Question bank created successfully", created));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateBank(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        bankService.deactivateBank(id, principal.getId(), isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Question bank deactivated successfully", null));
    }
}
