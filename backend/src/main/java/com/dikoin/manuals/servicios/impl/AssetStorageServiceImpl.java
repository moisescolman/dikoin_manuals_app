package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.config.StorageProperties;
import com.dikoin.manuals.dtos.asset.AssetResponse;
import com.dikoin.manuals.entidades.Asset;
import com.dikoin.manuals.entidades.Manual;
import com.dikoin.manuals.enums.AssetType;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.AssetMapper;
import com.dikoin.manuals.repositorios.AssetRepository;
import com.dikoin.manuals.repositorios.ManualRepository;
import com.dikoin.manuals.servicios.AssetStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetStorageServiceImpl implements AssetStorageService {

    private final StorageProperties storageProperties;
    private final AssetRepository assetRepository;
    private final ManualRepository manualRepository;
    private final AssetMapper assetMapper;

    @Override
    @Transactional
    public AssetResponse storeAsset(MultipartFile file, AssetType assetType, Long manualId) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("Archivo vacio");
        }

        Manual manual = manualId != null
                ? manualRepository.findById(manualId)
                .orElseThrow(() -> new ResourceNotFoundException("Manual no encontrado: " + manualId))
                : null;

        String folder = switch (assetType) {
            case LOGO -> "assets/logos";
            case TEMPLATE_RESOURCE -> "assets/templates";
            case EXTRACTED_IMAGE -> "assets/extracted";
            default -> manual != null ? "assets/manuals/" + safeName(manual.getCode()) : "assets/manuals/general";
        };

        Path stored = storeRawFile(file, folder);
        Asset asset = Asset.builder()
                .originalFilename(cleanFilename(file.getOriginalFilename()))
                .storedFilename(stored.getFileName().toString())
                .mimeType(normalizeMimeType(file))
                .fileSize(file.getSize())
                .storagePath(toRelative(stored))
                .assetType(assetType)
                .manual(manual)
                .build();
        return assetMapper.toResponse(assetRepository.save(asset));
    }

    @Override
    public Path storeRawFile(MultipartFile file, String folder) {
        try {
            Path targetFolder = basePath().resolve(folder).normalize();
            Files.createDirectories(targetFolder);
            Path destination = targetFolder.resolve(uniqueFilename(file.getOriginalFilename()));
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException ex) {
            throw new ApiException("No se pudo guardar el archivo", ex);
        }
    }

    @Override
    public Path resolve(String relativePath) {
        Path resolved = basePath().resolve(relativePath).normalize();
        if (!resolved.startsWith(basePath())) {
            throw new ApiException("Ruta de archivo no permitida");
        }
        return resolved;
    }

    @Override
    @Transactional(readOnly = true)
    public Path resolveAsset(Long assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset no encontrado: " + assetId));
        return resolve(asset.getStoragePath());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetResponse> findAll(Long manualId, AssetType assetType) {
        List<Asset> assets;
        if (manualId != null) {
            assets = assetRepository.findByManualId(manualId);
        } else if (assetType != null) {
            assets = assetRepository.findByAssetType(assetType);
        } else {
            assets = assetRepository.findAll();
        }
        return assets.stream().map(assetMapper::toResponse).toList();
    }

    private Path basePath() {
        return Path.of(storageProperties.getBasePath()).toAbsolutePath().normalize();
    }

    private String uniqueFilename(String originalFilename) {
        String clean = cleanFilename(originalFilename);
        String ext = extension(clean);
        String base = clean.substring(0, clean.length() - ext.length()).replaceAll("[^a-zA-Z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return timestamp + "_" + base + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
    }

    private String cleanFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "archivo";
        }
        return Path.of(originalFilename).getFileName().toString();
    }

    private String extension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 ? filename.substring(idx).toLowerCase() : "";
    }

    private String normalizeMimeType(MultipartFile file) {
        String filename = cleanFilename(file.getOriginalFilename()).toLowerCase();
        if (filename.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return file.getContentType();
    }

    private String safeName(String value) {
        return value == null ? "general" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String toRelative(Path stored) {
        return basePath().relativize(stored.toAbsolutePath().normalize()).toString().replace("\\", "/");
    }
}
