package com.dikoin.manuals.entidades;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(length = 160)
    private String companyName;

    @Column(length = 160)
    private String contactEmail;

    @Column(length = 80)
    private String contactPhone;

    @Column(length = 180)
    private String website;

    @Column(length = 700)
    private String logoPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_asset_id")
    private Asset logoAsset;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String headerConfigJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String footerConfigJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String layoutConfigJson;

    private boolean active;
    private boolean systemDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
