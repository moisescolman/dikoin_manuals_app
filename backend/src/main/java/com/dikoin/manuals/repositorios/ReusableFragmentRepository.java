package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ReusableFragment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReusableFragmentRepository extends JpaRepository<ReusableFragment, Long> {
    boolean existsByCodeIgnoreCase(String code);
    List<ReusableFragment> findByActiveTrueOrderByUpdatedAtDesc();
    List<ReusableFragment> findAllByOrderByUpdatedAtDesc();
    List<ReusableFragment> findByCodeStartingWithIgnoreCase(String prefix);
}
