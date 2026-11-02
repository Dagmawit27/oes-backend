package com.oes.onlineexamsystem.academic.service;

import com.oes.onlineexamsystem.academic.dto.DepartmentDto;
import com.oes.onlineexamsystem.academic.entity.Department;
import com.oes.onlineexamsystem.academic.repository.DepartmentRepository;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(DepartmentDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public DepartmentDto getDepartmentById(Long id) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
        return DepartmentDto.fromEntity(dept);
    }

    @Transactional
    public DepartmentDto createDepartment(DepartmentDto dto) {
        if (departmentRepository.existsByCodeIgnoreCase(dto.getCode().trim())) {
            throw new DuplicateResourceException("Department", "code", dto.getCode());
        }
        if (departmentRepository.existsByNameIgnoreCase(dto.getName().trim())) {
            throw new DuplicateResourceException("Department", "name", dto.getName());
        }

        Department department = Department.builder()
                .name(dto.getName().trim())
                .code(dto.getCode().trim().toUpperCase())
                .description(dto.getDescription())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return DepartmentDto.fromEntity(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentDto updateDepartment(Long id, DepartmentDto dto) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));

        department.setName(dto.getName().trim());
        department.setDescription(dto.getDescription());
        if (dto.getIsActive() != null) {
            department.setIsActive(dto.getIsActive());
        }

        return DepartmentDto.fromEntity(departmentRepository.save(department));
    }

    @Transactional
    public void deactivateDepartment(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
        department.setIsActive(false);
        departmentRepository.save(department);
    }
}
