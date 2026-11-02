package com.oes.onlineexamsystem.academic.service;

import com.oes.onlineexamsystem.academic.dto.BulkImportResultDto;
import com.oes.onlineexamsystem.academic.dto.StudentDto;
import com.oes.onlineexamsystem.academic.entity.Program;
import com.oes.onlineexamsystem.academic.entity.Student;
import com.oes.onlineexamsystem.academic.repository.ProgramRepository;
import com.oes.onlineexamsystem.academic.repository.StudentRepository;
import com.oes.onlineexamsystem.common.exception.BusinessRuleException;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import com.oes.onlineexamsystem.user.entity.Role;
import com.oes.onlineexamsystem.user.entity.RoleName;
import com.oes.onlineexamsystem.user.entity.User;
import com.oes.onlineexamsystem.user.repository.RoleRepository;
import com.oes.onlineexamsystem.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ProgramRepository programRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentService(StudentRepository studentRepository,
                          UserRepository userRepository,
                          ProgramRepository programRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.programRepository = programRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<StudentDto> getAllStudents() {
        return studentRepository.findAll().stream()
                .map(StudentDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentDto getStudentByUserId(Long userId) {
        Student student = studentRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student", "userId", userId));
        return StudentDto.fromEntity(student);
    }

    @Transactional
    public StudentDto createStudent(StudentDto dto) {
        if (studentRepository.existsByRegistrationNumberIgnoreCase(dto.getRegistrationNumber().trim())) {
            throw new DuplicateResourceException("Student", "registrationNumber", dto.getRegistrationNumber());
        }
        if (studentRepository.existsByUserId(dto.getUserId())) {
            throw new DuplicateResourceException("Student profile", "userId", dto.getUserId());
        }

        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", dto.getUserId()));

        Program program = programRepository.findById(dto.getProgramId())
                .orElseThrow(() -> new ResourceNotFoundException("Program", "id", dto.getProgramId()));

        Student student = Student.builder()
                .user(user)
                .program(program)
                .registrationNumber(dto.getRegistrationNumber().trim().toUpperCase())
                .yearOfStudy(dto.getYearOfStudy() != null ? dto.getYearOfStudy() : 1)
                .build();

        return StudentDto.fromEntity(studentRepository.save(student));
    }

    @Transactional
    public BulkImportResultDto importStudentsFromCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Please upload a valid, non-empty CSV file");
        }

        List<BulkImportResultDto.ImportErrorDetail> errors = new ArrayList<>();
        int total = 0;
        int success = 0;
        int failed = 0;

        Role studentRole = roleRepository.findByName(RoleName.ROLE_STUDENT)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", RoleName.ROLE_STUDENT.name()));

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            boolean headerSkipped = false;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Skip header if line matches common header keywords
                if (!headerSkipped) {
                    headerSkipped = true;
                    String lower = line.toLowerCase();
                    if (lower.contains("registration") || lower.contains("reg") || lower.contains("email")) {
                        continue;
                    }
                }

                total++;

                String[] tokens = line.split(",", -1);
                if (tokens.length < 5) {
                    failed++;
                    errors.add(new BulkImportResultDto.ImportErrorDetail(
                            lineNumber,
                            line,
                            "Insufficient columns. Expected: registrationNumber,firstName,lastName,email,programCode,[yearOfStudy]"
                    ));
                    continue;
                }

                String regNumber = tokens[0].trim().toUpperCase();
                String firstName = tokens[1].trim();
                String lastName = tokens[2].trim();
                String email = tokens[3].trim().toLowerCase();
                String programCode = tokens[4].trim().toUpperCase();
                int yearOfStudy = 1;
                if (tokens.length > 5 && !tokens[5].trim().isEmpty()) {
                    try {
                        yearOfStudy = Integer.parseInt(tokens[5].trim());
                    } catch (NumberFormatException ignored) {
                        yearOfStudy = 1;
                    }
                }

                if (regNumber.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || programCode.isEmpty()) {
                    failed++;
                    errors.add(new BulkImportResultDto.ImportErrorDetail(
                            lineNumber,
                            regNumber.isEmpty() ? email : regNumber,
                            "Missing required fields (registrationNumber, firstName, lastName, email, programCode)"
                    ));
                    continue;
                }

                if (!email.contains("@") || !email.contains(".")) {
                    failed++;
                    errors.add(new BulkImportResultDto.ImportErrorDetail(lineNumber, email, "Invalid email address format"));
                    continue;
                }

                if (studentRepository.existsByRegistrationNumberIgnoreCase(regNumber)) {
                    failed++;
                    errors.add(new BulkImportResultDto.ImportErrorDetail(
                            lineNumber,
                            regNumber,
                            "Student with registration number " + regNumber + " already exists"
                    ));
                    continue;
                }

                var programOpt = programRepository.findByCodeIgnoreCase(programCode);
                if (programOpt.isEmpty()) {
                    failed++;
                    errors.add(new BulkImportResultDto.ImportErrorDetail(
                            lineNumber,
                            regNumber,
                            "Program with code '" + programCode + "' does not exist"
                    ));
                    continue;
                }

                // Check or create User
                User user;
                var userOpt = userRepository.findByEmailIgnoreCase(email);
                if (userOpt.isPresent()) {
                    user = userOpt.get();
                    if (studentRepository.existsByUserId(user.getId())) {
                        failed++;
                        errors.add(new BulkImportResultDto.ImportErrorDetail(
                                lineNumber,
                                email,
                                "User account with email " + email + " is already associated with a student record"
                        ));
                        continue;
                    }
                    user.getRoles().add(studentRole);
                    userRepository.save(user);
                } else {
                    user = User.builder()
                            .email(email)
                            .firstName(firstName)
                            .lastName(lastName)
                            .passwordHash(passwordEncoder.encode("Student@123"))
                            .isActive(true)
                            .roles(new HashSet<>(Set.of(studentRole)))
                            .build();
                    user = userRepository.save(user);
                }

                Student student = Student.builder()
                        .user(user)
                        .program(programOpt.get())
                        .registrationNumber(regNumber)
                        .yearOfStudy(yearOfStudy)
                        .build();

                studentRepository.save(student);
                success++;
            }
        } catch (Exception e) {
            throw new BusinessRuleException("Failed to process student CSV file: " + e.getMessage());
        }

        return BulkImportResultDto.builder()
                .totalRecords(total)
                .successfulRecords(success)
                .failedRecords(failed)
                .errors(errors)
                .build();
    }
}
