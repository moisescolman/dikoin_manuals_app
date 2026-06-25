package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ReusableBlock;
import com.dikoin.manuals.enums.ReusableType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReusableBlockRepository extends JpaRepository<ReusableBlock, Long> {
    boolean existsByCodeIgnoreCase(String code);
    List<ReusableBlock> findByActiveTrueOrderByUpdatedAtDesc();
    List<ReusableBlock> findAllByOrderByUpdatedAtDesc();
    List<ReusableBlock> findByReusableTypeAndActiveTrueOrderByUpdatedAtDesc(ReusableType reusableType);
    List<ReusableBlock> findByReusableTypeOrderByUpdatedAtDesc(ReusableType reusableType);
    List<ReusableBlock> findByCodeStartingWithIgnoreCase(String prefix);
}
