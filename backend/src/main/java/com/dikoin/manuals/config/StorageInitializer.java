package com.dikoin.manuals.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class StorageInitializer {

    private final StorageProperties storageProperties;

    @PostConstruct
    void createStorageTree() throws IOException {
        Path base = Path.of(storageProperties.getBasePath());
        Files.createDirectories(base.resolve("imports/docx"));
        Files.createDirectories(base.resolve("imports/doc"));
        Files.createDirectories(base.resolve("imports/odt"));
        Files.createDirectories(base.resolve("imports/pdf"));
        Files.createDirectories(base.resolve("assets/manuals"));
        Files.createDirectories(base.resolve("assets/templates"));
        Files.createDirectories(base.resolve("assets/logos"));
        Files.createDirectories(base.resolve("assets/extracted"));
        Files.createDirectories(base.resolve("exports/pdf"));
        Files.createDirectories(base.resolve("thumbnails"));
        Files.createDirectories(base.resolve("temp"));
    }
}
