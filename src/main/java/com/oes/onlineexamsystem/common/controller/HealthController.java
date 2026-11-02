package com.oes.onlineexamsystem.common.controller;

import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.common.dto.ValidationTestRequest;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.common.exception.UnauthorizedException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final DataSource dataSource;

    @Autowired
    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth() {
        Map<String, Object> healthInfo = new LinkedHashMap<>();
        healthInfo.put("application", "Online Examination System");
        healthInfo.put("status", "UP");
        healthInfo.put("timestamp", Instant.now().toString());

        Map<String, Object> dbInfo = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT current_database(), version(), count(*) FROM system_health_check");
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                dbInfo.put("status", "UP");
                dbInfo.put("database", rs.getString(1));
                dbInfo.put("version", rs.getString(2));
                dbInfo.put("healthCheckRecords", rs.getLong(3));
            }
        } catch (Exception e) {
            dbInfo.put("status", "DOWN");
            dbInfo.put("error", e.getMessage());
            healthInfo.put("status", "DEGRADED");
        }

        healthInfo.put("database", dbInfo);
        return ResponseEntity.ok(ApiResponse.success("System health status", healthInfo));
    }

    @PostMapping("/test-validation")
    public ResponseEntity<ApiResponse<ValidationTestRequest>> testValidation(
            @Valid @RequestBody ValidationTestRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Validation passed successfully", request));
    }

    @GetMapping("/test-error/{type}")
    public ResponseEntity<Void> testError(@PathVariable String type) {
        switch (type.toLowerCase()) {
            case "not-found":
                throw new ResourceNotFoundException("Course", "id", 999);
            case "duplicate":
                throw new DuplicateResourceException("User", "email", "test@college.edu");
            case "business-rule":
                throw new BusinessRuleException("Examination cannot be submitted without selecting questions");
            case "unauthorized":
                throw new UnauthorizedException("You do not have permission to perform this action");
            default:
                throw new RuntimeException("Simulated unexpected internal server error");
        }
    }
}
