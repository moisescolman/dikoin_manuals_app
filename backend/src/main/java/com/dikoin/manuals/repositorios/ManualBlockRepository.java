package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ManualBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManualBlockRepository extends JpaRepository<ManualBlock, Long> {
    List<ManualBlock> findBySectionIdOrderBySortOrderAsc(Long sectionId);
    List<ManualBlock> findByContentJsonContaining(String token);
}
