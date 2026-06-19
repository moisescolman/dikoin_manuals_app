package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.Manual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ManualRepository extends JpaRepository<Manual, Long> {
    Optional<Manual> findByCodeIgnoreCase(String code);
    Optional<Manual> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByCodeIgnoreCase(String code);
    long countByDeletedAtIsNull();

    @Query("""
            select m from Manual m
            join fetch m.product p
            left join fetch m.documentType dt
            where m.deletedAt is null
            and (
                :search is null
                or lower(m.code) like lower(concat('%', :search, '%'))
                or lower(m.title) like lower(concat('%', :search, '%'))
                or lower(coalesce(m.titleEs, '')) like lower(concat('%', :search, '%'))
                or lower(coalesce(m.titleEn, '')) like lower(concat('%', :search, '%'))
                or lower(coalesce(dt.code, '')) like lower(concat('%', :search, '%'))
                or lower(coalesce(dt.name, '')) like lower(concat('%', :search, '%'))
                or lower(p.code) like lower(concat('%', :search, '%'))
                or lower(p.name) like lower(concat('%', :search, '%'))
            )
            order by m.updatedAt desc
            """)
    List<Manual> searchActive(@Param("search") String search);
}
