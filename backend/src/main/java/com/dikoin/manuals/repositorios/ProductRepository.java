package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
    List<Product> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(String code, String name);
}
