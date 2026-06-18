package com.dikoin.manuals.repositorios;

import com.dikoin.manuals.entidades.Asset;
import com.dikoin.manuals.enums.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByManualId(Long manualId);
    List<Asset> findByAssetType(AssetType assetType);
}
