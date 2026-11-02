package com.oes.onlineexamsystem.academic.service;

import com.oes.onlineexamsystem.academic.dto.AcademicYearDto;
import com.oes.onlineexamsystem.academic.dto.SemesterDto;
import com.oes.onlineexamsystem.academic.entity.AcademicYear;
import com.oes.onlineexamsystem.academic.entity.Semester;
import com.oes.onlineexamsystem.academic.repository.AcademicYearRepository;
import com.oes.onlineexamsystem.academic.repository.SemesterRepository;
import com.oes.onlineexamsystem.common.exception.DuplicateResourceException;
import com.oes.onlineexamsystem.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AcademicYearService {

    private final AcademicYearRepository academicYearRepository;
    private final SemesterRepository semesterRepository;

    public AcademicYearService(AcademicYearRepository academicYearRepository, SemesterRepository semesterRepository) {
        this.academicYearRepository = academicYearRepository;
        this.semesterRepository = semesterRepository;
    }

    @Transactional(readOnly = true)
    public List<AcademicYearDto> getAllAcademicYears() {
        return academicYearRepository.findAll().stream()
                .map(AcademicYearDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SemesterDto> getAllSemesters() {
        return semesterRepository.findAll().stream()
                .map(SemesterDto::fromEntity)
                .toList();
    }

    @Transactional
    public AcademicYearDto createAcademicYear(AcademicYearDto dto) {
        if (academicYearRepository.existsByName(dto.getName().trim())) {
            throw new DuplicateResourceException("AcademicYear", "name", dto.getName());
        }

        AcademicYear year = AcademicYear.builder()
                .name(dto.getName().trim())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .isCurrent(dto.getIsCurrent() != null ? dto.getIsCurrent() : false)
                .build();

        return AcademicYearDto.fromEntity(academicYearRepository.save(year));
    }

    @Transactional(readOnly = true)
    public List<SemesterDto> getSemestersByAcademicYear(Long academicYearId) {
        return semesterRepository.findByAcademicYearId(academicYearId).stream()
                .map(SemesterDto::fromEntity)
                .toList();
    }

    @Transactional
    public SemesterDto createSemester(SemesterDto dto) {
        if (semesterRepository.existsByAcademicYearIdAndSemesterNumber(dto.getAcademicYearId(), dto.getSemesterNumber())) {
            throw new DuplicateResourceException("Semester", "semesterNumber", dto.getSemesterNumber());
        }

        AcademicYear academicYear = academicYearRepository.findById(dto.getAcademicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", "id", dto.getAcademicYearId()));

        Semester semester = Semester.builder()
                .academicYear(academicYear)
                .name(dto.getName().trim())
                .semesterNumber(dto.getSemesterNumber())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .isCurrent(dto.getIsCurrent() != null ? dto.getIsCurrent() : false)
                .build();

        return SemesterDto.fromEntity(semesterRepository.save(semester));
    }
}
