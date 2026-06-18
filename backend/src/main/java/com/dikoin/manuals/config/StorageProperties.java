package com.dikoin.manuals.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "manuals.storage")
public class StorageProperties {
    private String basePath = "./storage";
    private long maxDocumentSizeMb = 100;
    private long maxImageSizeMb = 20;
}
