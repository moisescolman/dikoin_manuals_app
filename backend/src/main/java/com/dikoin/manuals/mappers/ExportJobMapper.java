package com.dikoin.manuals.mappers;

import com.dikoin.manuals.dtos.exportjob.ExportJobResponse;
import com.dikoin.manuals.entidades.ExportJob;
import org.springframework.stereotype.Component;

@Component
public class ExportJobMapper {
    public ExportJobResponse toResponse(ExportJob job) {
        return new ExportJobResponse(
                job.getId(),
                job.getManualVersion().getId(),
                job.getStatus(),
                job.getPdfPath(),
                job.getLogMessage(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }
}
