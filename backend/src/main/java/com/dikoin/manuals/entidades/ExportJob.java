package com.dikoin.manuals.entidades;

import com.dikoin.manuals.enums.ExportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "export_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_version_id")
    private ManualVersion manualVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExportStatus status;

    @Column(length = 700)
    private String pdfPath;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String logMessage;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = ExportStatus.PENDING;
        }
    }
}
