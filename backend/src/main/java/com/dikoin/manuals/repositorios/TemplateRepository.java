package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository extends JpaRepository<Template, Long> {
    Optional<Template> findByActiveTrue();
    List<Template> findByNameContainingIgnoreCaseOrderByUpdatedAtDesc(String name);
}
