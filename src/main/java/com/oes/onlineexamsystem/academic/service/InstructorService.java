package com.oes.onlineexamsystem.academic.service;

import com.oes.onlineexamsystem.academic.dto.InstructorDto;
import com.oes.onlineexamsystem.academic.entity.Department;
import com.oes.onlineexamsystem.academic.entity.Instructor;
import com.oes.onlineexamsystem.academic.repository.DepartmentRepository;
import com.oes.onlineexamsystem.academic.repository.InstructorRepository;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.user.entity.RoleName;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InstructorService {

    private final InstructorRepository instructorRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public InstructorService(InstructorRepository instructorRepository,
                             UserRepository userRepository,
                             DepartmentRepository departmentRepository) {
        this.instructorRepository = instructorRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional
    public List<InstructorDto> getAllInstructors() {
        syncInstructorsFromUsers();
        return instructorRepository.findAll().stream()
                .map(InstructorDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public InstructorDto getInstructorByUserId(Long userId) {
        Instructor instructor = instructorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Instructor", "userId", userId));
        return InstructorDto.fromEntity(instructor);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncInstructorsFromUsers() {
        List<User> instructorUsers = userRepository.findAll().stream()
                .filter(u -> u.getRoles() != null && u.getRoles().stream()
                        .anyMatch(r -> r.getName() == RoleName.ROLE_INSTRUCTOR))
                .toList();

        List<Department> departments = departmentRepository.findAll();
        Department defaultDept = departments.isEmpty() ? null : departments.get(0);

        for (User u : instructorUsers) {
            if (!instructorRepository.existsByUserId(u.getId())) {
                if (defaultDept != null) {
                    String baseEmpId = "INS-" + String.format("%03d", u.getId());
                    String empId = baseEmpId;
                    if (instructorRepository.existsByEmployeeIdIgnoreCase(empId)) {
                        empId = "INS-" + u.getId() + "-" + (System.currentTimeMillis() % 1000);
                    }
                    Instructor instructor = Instructor.builder()
                            .user(u)
                            .department(defaultDept)
                            .employeeId(empId)
                            .specialization("General Faculty")
                            .build();
                    instructorRepository.save(instructor);
                }
            }
        }
    }

    @Transactional
    public InstructorDto createInstructor(InstructorDto dto) {
        if (instructorRepository.existsByEmployeeIdIgnoreCase(dto.getEmployeeId().trim())) {
            throw new DuplicateResourceException("Instructor", "employeeId", dto.getEmployeeId());
        }
        if (instructorRepository.existsByUserId(dto.getUserId())) {
            throw new DuplicateResourceException("Instructor profile", "userId", dto.getUserId());
        }

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", dto.getUserId()));

        Department department = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", dto.getDepartmentId()));

        Instructor instructor = Instructor.builder()
                .user(user)
                .department(department)
                .employeeId(dto.getEmployeeId().trim().toUpperCase())
                .specialization(dto.getSpecialization())
                .build();

        return InstructorDto.fromEntity(instructorRepository.save(instructor));
    }
}
