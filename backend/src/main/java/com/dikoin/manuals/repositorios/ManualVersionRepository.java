package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ManualVersion;
import com.dikoin.manuals.enums.ManualStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManualVersionRepository extends JpaRepository<ManualVersion, Long> {
    List<ManualVersion> findByManualIdOrderByCreatedAtDesc(Long manualId);
    Optional<ManualVersion> findByManualIdAndActiveTrue(Long manualId);
    long countByStatus(ManualStatus status);
    long countByEnReadyFalse();
    long countByStatusAndActiveTrueAndManualDeletedAtIsNull(ManualStatus status);
    long countByEnReadyFalseAndActiveTrueAndManualDeletedAtIsNull();
}
