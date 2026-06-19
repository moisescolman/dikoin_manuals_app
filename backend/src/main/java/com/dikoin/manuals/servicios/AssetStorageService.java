package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.asset.AssetResponse;
import com.dikoin.manuals.enums.AssetType;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

public interface AssetStorageService {
    AssetResponse storeAsset(MultipartFile file, AssetType assetType, Long manualId);
    Path storeRawFile(MultipartFile file, String folder);
    Path resolve(String relativePath);
    Path resolveAsset(Long assetId);
    Path resolveThumbnail(Long assetId);
    List<AssetResponse> findAll(Long manualId, AssetType assetType);
    void delete(Long assetId);
}
