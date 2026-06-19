package com.dikoin.manuals.dtos.asset;

import com.dikoin.manuals.enums.AssetType;

import java.time.LocalDateTime;

public record AssetResponse(
        Long id,
        String originalFilename,
        String storedFilename,
        String mimeType,
        Long fileSize,
        String storagePath,
        String thumbnailPath,
        String fileUrl,
        String thumbnailUrl,
        AssetType assetType,
        Long manualId,
        LocalDateTime createdAt
) {
}
