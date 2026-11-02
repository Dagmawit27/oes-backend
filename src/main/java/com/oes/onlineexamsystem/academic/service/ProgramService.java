package com.oes.onlineexamsystem.academic.service;

import com.oes.onlineexamsystem.academic.dto.ProgramDto;
import com.oes.onlineexamsystem.academic.entity.Department;
import com.oes.onlineexamsystem.academic.entity.Program;
import com.oes.onlineexamsystem.academic.repository.DepartmentRepository;
import com.oes.onlineexamsystem.academic.repository.ProgramRepository;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProgramService {

    private final ProgramRepository programRepository;
    private final DepartmentRepository departmentRepository;

    public ProgramService(ProgramRepository programRepository, DepartmentRepository departmentRepository) {
        this.programRepository = programRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<ProgramDto> getAllPrograms() {
        return programRepository.findAll().stream()
                .map(ProgramDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgramDto getProgramById(Long id) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));
        return ProgramDto.fromEntity(program);
    }

    @Transactional
    public ProgramDto createProgram(ProgramDto dto) {
        if (programRepository.existsByCodeIgnoreCase(dto.getCode().trim())) {
            throw new DuplicateResourceException("Program", "code", dto.getCode());
        }

        Department department = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", dto.getDepartmentId()));

        Program program = Program.builder()
                .department(department)
                .name(dto.getName().trim())
                .code(dto.getCode().trim().toUpperCase())
                .degreeType(dto.getDegreeType().trim())
                .durationYears(dto.getDurationYears() != null ? dto.getDurationYears() : 4)
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return ProgramDto.fromEntity(programRepository.save(program));
    }

    @Transactional
    public void deactivateProgram(Long id) {
        Program program = programRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", id));
        program.setIsActive(false);
        programRepository.save(program);
    }
}
