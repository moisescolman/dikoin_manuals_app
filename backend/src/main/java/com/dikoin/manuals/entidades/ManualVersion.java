package com.dikoin.manuals.entidades;

import com.dikoin.manuals.enums.ManualStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "manual_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_id")
    private Manual manual;

    @Column(nullable = false, length = 40)
    private String versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ManualStatus status;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean esReady;

    @Column(nullable = false)
    private boolean enReady;

    @Column(length = 600)
    private String changeNotes;

    @OneToMany(mappedBy = "manualVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<ManualSection> sections = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) {
            status = ManualStatus.DRAFT;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
