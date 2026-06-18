package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.NoticeTemplate;
import com.dikoin.manuals.enums.NoticeType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeTemplateRepository extends JpaRepository<NoticeTemplate, Long> {
    boolean existsByCodeIgnoreCase(String code);
    List<NoticeTemplate> findByActiveTrueOrderByUpdatedAtDesc();
    List<NoticeTemplate> findByTypeAndActiveTrueOrderByUpdatedAtDesc(NoticeType type);
}
