package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.NoticeApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeApplicationRepository extends JpaRepository<NoticeApplication, Long> {
    List<NoticeApplication> findByManualId(Long manualId);
    List<NoticeApplication> findByProductId(Long productId);
    List<NoticeApplication> findBySectionId(Long sectionId);
}
