package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {
    List<TemplateVersion> findByTemplateIdOrderByCreatedAtDesc(Long templateId);
}
