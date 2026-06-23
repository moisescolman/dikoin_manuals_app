package com.dikoin.manuals.entidades;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_families")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductFamily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 160)
    private String nameEs;

    @Column(length = 160)
    private String nameEn;
}
