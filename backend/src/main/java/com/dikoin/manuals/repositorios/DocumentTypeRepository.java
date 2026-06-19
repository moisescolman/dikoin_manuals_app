package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {
    List<DocumentType> findByActiveTrueOrderBySortOrderAscNameAsc();
    Optional<DocumentType> findByCodeIgnoreCase(String code);
}
