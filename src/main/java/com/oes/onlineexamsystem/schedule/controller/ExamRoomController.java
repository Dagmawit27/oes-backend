package com.oes.onlineexamsystem.schedule.controller;

import com.oes.onlineexamsystem.common.dto.ApiResponse;
import com.oes.onlineexamsystem.schedule.dto.CreateRoomRequest;
import com.oes.onlineexamsystem.schedule.dto.ExamRoomDto;
import com.oes.onlineexamsystem.schedule.service.ExamRoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exam-rooms")
public class ExamRoomController {

    private final ExamRoomService roomService;

    public ExamRoomController(ExamRoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<ExamRoomDto>>> getActiveRooms() {
        List<ExamRoomDto> rooms = roomService.getActiveRooms();
        return ResponseEntity.ok(ApiResponse.success("Exam rooms retrieved successfully", rooms));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExamRoomDto>> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        ExamRoomDto created = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Exam room created successfully", created));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EXAM_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateRoom(@PathVariable Long id) {
        roomService.deactivateRoom(id);
        return ResponseEntity.ok(ApiResponse.success("Exam room deactivated successfully", null));
    }
}
