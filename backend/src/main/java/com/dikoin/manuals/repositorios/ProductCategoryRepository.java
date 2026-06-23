package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    Optional<ProductCategory> findByCodeIgnoreCase(String code);
    List<ProductCategory> findAllByOrderByCodeAsc();
}
