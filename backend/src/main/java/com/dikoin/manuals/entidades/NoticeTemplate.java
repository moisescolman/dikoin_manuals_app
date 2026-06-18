package com.dikoin.manuals.entidades;

import com.dikoin.manuals.enums.NoticeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notice_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoticeTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NoticeType type;

    @Column(nullable = false, length = 220)
    private String titleEs;

    @Column(length = 220)
    private String titleEn;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String contentEs;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String contentEn;

    @Column(nullable = false)
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        active = true;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
