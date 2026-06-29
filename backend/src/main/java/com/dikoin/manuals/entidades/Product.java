package com.dikoin.manuals.entidades;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "name_es", length = 160)
    private String nameEs;

    @Column(name = "name_en", length = 160)
    private String nameEn;

    @Column(length = 120)
    private String family;

    @Column(name = "family_code", length = 20)
    private String familyCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_id")
    private ProductFamily productFamily;

    @Column(length = 120)
    private String category;

    @Column(name = "category_codes", length = 120)
    private String categoryCodes;

    @ManyToMany
    @JoinTable(
            name = "product_category_assignments",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default
    private Set<ProductCategory> categories = new LinkedHashSet<>();

    @Column(length = 600)
    private String description;

    @Column(name = "description_es", length = 600)
    private String descriptionEs;

    @Column(name = "description_en", length = 600)
    private String descriptionEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_image_asset_id")
    private Asset productImageAsset;

    @Column(nullable = false)
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        active = true;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
