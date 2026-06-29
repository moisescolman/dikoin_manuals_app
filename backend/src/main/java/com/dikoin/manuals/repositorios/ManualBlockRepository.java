package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ManualBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ManualBlockRepository extends JpaRepository<ManualBlock, Long> {
    List<ManualBlock> findBySectionIdOrderBySortOrderAsc(Long sectionId);
    List<ManualBlock> findByIdIn(List<Long> ids);
    List<ManualBlock> findByReusableBlockId(Long reusableBlockId);
    List<ManualBlock> findByReusableFragmentId(Long reusableFragmentId);
    List<ManualBlock> findByContentJsonContaining(String token);
}
