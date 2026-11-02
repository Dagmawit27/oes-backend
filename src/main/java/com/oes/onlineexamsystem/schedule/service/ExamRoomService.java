package com.oes.onlineexamsystem.schedule.service;

import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.schedule.dto.CreateRoomRequest;
import com.oes.onlineexamsystem.schedule.dto.ExamRoomDto;
import com.oes.onlineexamsystem.schedule.entity.ExamRoom;
import com.oes.onlineexamsystem.schedule.repository.ExamRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExamRoomService {

    private final ExamRoomRepository roomRepository;

    public ExamRoomService(ExamRoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<ExamRoomDto> getActiveRooms() {
        return roomRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(ExamRoomDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamRoomDto> getAllRooms() {
        return roomRepository.findAll().stream()
                .map(ExamRoomDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExamRoomDto getRoomById(Long id) {
        ExamRoom room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Room", "id", id));
        return ExamRoomDto.fromEntity(room);
    }

    @Transactional
    public ExamRoomDto createRoom(CreateRoomRequest request) {
        if (roomRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new BusinessRuleException("A room with name '" + request.getName().trim() + "' already exists.");
        }

        ExamRoom room = ExamRoom.builder()
                .name(request.getName().trim())
                .building(request.getBuilding() != null ? request.getBuilding().trim() : null)
                .capacity(request.getCapacity())
                .isActive(true)
                .build();

        ExamRoom saved = roomRepository.save(room);
        return ExamRoomDto.fromEntity(saved);
    }

    @Transactional
    public void deactivateRoom(Long id) {
        ExamRoom room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam Room", "id", id));
        room.setIsActive(false);
        roomRepository.save(room);
    }
}
