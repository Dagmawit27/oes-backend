package com.oes.onlineexamsystem.result.controller;

import com.oes.onlineexamsystem.auth.security.UserPrincipal;
import com.oes.onlineexamsystem.result.service.GradingAndResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
public class ResultExportController {

    private final GradingAndResultService gradingAndResultService;

    /**
     * Export examination results to CSV format.
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ROLE_INSTRUCTOR', 'ROLE_EXAM_OFFICER', 'ROLE_ADMIN')")
    public ResponseEntity<byte[]> exportResults(
            @RequestParam Long examId,
            @AuthenticationPrincipal UserPrincipal principal) {

        boolean isAdmin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOfficer = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_EXAM_OFFICER"));

        String csvContent = gradingAndResultService.generateResultsCsv(examId, principal.getId(), isAdmin, isOfficer);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "examination-" + examId + "-results.csv");

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
