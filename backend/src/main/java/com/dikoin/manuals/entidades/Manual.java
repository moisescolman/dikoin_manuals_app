package com.dikoin.manuals.entidades;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "manuals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Manual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(length = 220)
    private String titleEs;

    @Column(length = 220)
    private String titleEn;

    @Column(length = 120)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_type_id")
    private DocumentType documentType;

    @Column(length = 2)
    private String documentYear;

    @Column(length = 2)
    private String documentVersion;

    @Column(length = 10)
    private String languageCode;

    @Column(nullable = false)
    private boolean enabled;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @OneToMany(mappedBy = "manual", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    @Builder.Default
    private List<ManualVersion> versions = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        enabled = true;
        if (titleEs == null || titleEs.isBlank()) {
            titleEs = title;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
