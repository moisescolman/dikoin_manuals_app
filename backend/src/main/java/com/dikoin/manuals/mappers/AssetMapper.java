package com.dikoin.manuals.mappers;

import com.dikoin.manuals.dtos.asset.AssetResponse;
import com.dikoin.manuals.entidades.Asset;
import org.springframework.stereotype.Component;

@Component
public class AssetMapper {
    public AssetResponse toResponse(Asset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getOriginalFilename(),
                asset.getStoredFilename(),
                asset.getMimeType(),
                asset.getFileSize(),
                asset.getStoragePath(),
                asset.getThumbnailPath(),
                "/api/v1/assets/" + asset.getId() + "/file",
                asset.getThumbnailPath() != null ? "/api/v1/assets/" + asset.getId() + "/thumbnail" : null,
                asset.getAssetType(),
                asset.getManual() != null ? asset.getManual().getId() : null,
                asset.getCreatedAt()
        );
    }
}
