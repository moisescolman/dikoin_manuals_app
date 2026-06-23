package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ProductFamily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductFamilyRepository extends JpaRepository<ProductFamily, Long> {
    Optional<ProductFamily> findByCodeIgnoreCase(String code);
    List<ProductFamily> findAllByOrderByCodeAsc();
}
