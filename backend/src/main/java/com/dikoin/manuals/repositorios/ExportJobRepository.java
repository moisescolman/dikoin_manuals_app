package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ExportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExportJobRepository extends JpaRepository<ExportJob, Long> {
    List<ExportJob> findByManualVersionManualIdOrderByCreatedAtDesc(Long manualId);
    List<ExportJob> findByManualVersionManualId(Long manualId);
}
