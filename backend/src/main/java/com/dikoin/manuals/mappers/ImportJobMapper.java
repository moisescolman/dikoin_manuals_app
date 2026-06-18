package com.dikoin.manuals.mappers;

import com.dikoin.manuals.dtos.importjob.ImportJobResponse;
import com.dikoin.manuals.entidades.ImportJob;
import org.springframework.stereotype.Component;

@Component
public class ImportJobMapper {
    public ImportJobResponse toResponse(ImportJob job) {
        return new ImportJobResponse(
                job.getId(),
                job.getSourceFilename(),
                job.getStoredPath(),
                job.getFileExtension(),
                job.getStatus(),
                job.getLanguageCode(),
                job.getManualVersion() != null ? job.getManualVersion().getId() : null,
                job.getDetectedSections(),
                job.getDetectedTables(),
                job.getDetectedImages(),
                job.getLogMessage(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }
}
