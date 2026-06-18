package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ReusableBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReusableBlockRepository extends JpaRepository<ReusableBlock, Long> {
    boolean existsByCodeIgnoreCase(String code);
    List<ReusableBlock> findByActiveTrueOrderByUpdatedAtDesc();
    List<ReusableBlock> findAllByOrderByUpdatedAtDesc();
}
