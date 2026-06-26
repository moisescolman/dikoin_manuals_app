package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.asset.AssetResponse;
import com.dikoin.manuals.enums.AssetType;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.servicios.AssetStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetRestController {

    private final AssetStorageService assetStorageService;

    @GetMapping
    public List<AssetResponse> findAll(
            @RequestParam(required = false) Long manualId,
            @RequestParam(required = false) AssetType assetType
    ) {
        return assetStorageService.findAll(manualId, assetType);
    }

    @PostMapping
    public AssetResponse upload(
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "IMAGE") AssetType assetType,
            @RequestParam(required = false) Long manualId
    ) {
        return assetStorageService.storeAsset(file, assetType, manualId);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) throws MalformedURLException {
        Path path = assetStorageService.resolveAsset(id);
        return resourceResponse(path);
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Resource> thumbnail(@PathVariable Long id) throws MalformedURLException {
        Path path = assetStorageService.resolveThumbnail(id);
        return resourceResponse(path);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        assetStorageService.delete(id);
    }

    private ResponseEntity<Resource> resourceResponse(Path path) throws MalformedURLException {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new ResourceNotFoundException("Archivo no encontrado");
        }
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new ResourceNotFoundException("Archivo no encontrado");
        }
        String contentType;
        try {
            contentType = Files.probeContentType(path);
        } catch (Exception ex) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        if (contentType == null && path.getFileName().toString().toLowerCase().endsWith(".svg")) {
            contentType = "image/svg+xml";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType))
                .body(resource);
    }
}
