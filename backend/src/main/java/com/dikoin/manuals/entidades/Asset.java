package com.dikoin.manuals.entidades;

import com.dikoin.manuals.enums.AssetType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 255)
    private String storedFilename;

    @Column(length = 120)
    private String mimeType;

    private Long fileSize;

    @Column(nullable = false, length = 700)
    private String storagePath;

    @Column(length = 700)
    private String thumbnailPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AssetType assetType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_id")
    private Manual manual;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
