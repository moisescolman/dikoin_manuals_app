package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    List<ImportJob> findTop10ByOrderByCreatedAtDesc();
}
