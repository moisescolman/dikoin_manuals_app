package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);

    @Query("""
            select distinct p
            from Product p
            left join fetch p.productFamily f
            left join fetch p.categories c
            where :search is null
               or lower(p.code) like lower(concat('%', :search, '%'))
               or lower(p.name) like lower(concat('%', :search, '%'))
               or lower(p.nameEs) like lower(concat('%', :search, '%'))
               or lower(p.nameEn) like lower(concat('%', :search, '%'))
               or lower(f.code) like lower(concat('%', :search, '%'))
               or lower(f.nameEs) like lower(concat('%', :search, '%'))
               or lower(c.code) like lower(concat('%', :search, '%'))
               or lower(c.nameEs) like lower(concat('%', :search, '%'))
            order by p.code
            """)
    List<Product> search(@Param("search") String search);

    @Query("""
            select distinct p
            from Product p
            left join fetch p.productFamily
            left join fetch p.categories
            order by p.code
            """)
    List<Product> findAllWithTaxonomy();
}
