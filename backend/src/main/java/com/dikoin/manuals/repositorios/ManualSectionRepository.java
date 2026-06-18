package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ManualSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManualSectionRepository extends JpaRepository<ManualSection, Long> {
    List<ManualSection> findByManualVersionIdOrderBySortOrderAsc(Long manualVersionId);
}
