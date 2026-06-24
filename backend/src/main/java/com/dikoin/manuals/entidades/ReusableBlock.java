package com.dikoin.manuals.entidades;

import com.dikoin.manuals.enums.ReusableType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reusable_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReusableBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 220)
    private String title;

    @Column(length = 600)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ReusableType reusableType = ReusableType.SINGLE_BLOCK;

    @Column(length = 120)
    private String productCategory;

    @Column(length = 600)
    private String productCodes;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String contentJson;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (reusableType == null) {
            reusableType = ReusableType.SINGLE_BLOCK;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
