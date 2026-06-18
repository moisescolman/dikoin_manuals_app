package com.dikoin.manuals.entidades;

import com.dikoin.manuals.enums.ImportStatus;
import com.dikoin.manuals.enums.LanguageCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "import_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String sourceFilename;

    @Column(nullable = false, length = 700)
    private String storedPath;

    @Column(length = 80)
    private String fileExtension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LanguageCode languageCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_version_id")
    private ManualVersion manualVersion;

    private Integer detectedSections;
    private Integer detectedTables;
    private Integer detectedImages;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String logMessage;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ImportStatus.PENDING;
        }
    }
}
