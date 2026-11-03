package com.oes.onlineexamsystem.result.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveGradesRequest {

    @NotNull(message = "Grades list cannot be null")
    @Valid
    private List<SaveGradeItemRequest> grades;
}
