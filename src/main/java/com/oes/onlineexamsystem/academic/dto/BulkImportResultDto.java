package com.oes.onlineexamsystem.academic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportResultDto {
    private int totalRecords;
    private int successfulRecords;
    private int failedRecords;

    @Builder.Default
    private List<ImportErrorDetail> errors = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportErrorDetail {
        private int rowNumber;
        private String identifier;
        private String message;
    }
}
