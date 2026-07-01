package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.asset.AssetResponse;
import com.dikoin.manuals.dtos.template.TemplateRequest;
import com.dikoin.manuals.dtos.template.TemplateResponse;
import com.dikoin.manuals.dtos.template.TemplateVersionRequest;
import com.dikoin.manuals.dtos.template.TemplateVersionResponse;
import com.dikoin.manuals.entidades.Asset;
import com.dikoin.manuals.entidades.Template;
import com.dikoin.manuals.entidades.TemplateVersion;
import com.dikoin.manuals.enums.AssetType;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.TemplateMapper;
import com.dikoin.manuals.repositorios.AssetRepository;
import com.dikoin.manuals.repositorios.TemplateRepository;
import com.dikoin.manuals.repositorios.TemplateVersionRepository;
import com.dikoin.manuals.servicios.AssetStorageService;
import com.dikoin.manuals.servicios.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository templateRepository;
    private final TemplateVersionRepository templateVersionRepository;
    private final AssetRepository assetRepository;
    private final AssetStorageService assetStorageService;
    private final TemplateMapper templateMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<TemplateResponse> findAll() {
        return templateRepository.findAll().stream().map(templateMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse findActive() {
        return templateRepository.findByActiveTrue()
                .map(templateMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No hay plantilla activa"));
    }

    @Override
    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        Template template = templateMapper.toEntity(request, findLogoAsset(request.logoAssetId()));
        if (request.active()) {
            templateRepository.findAll().forEach(t -> t.setActive(false));
        }
        return templateMapper.toResponse(templateRepository.save(template));
    }

    @Override
    @Transactional
    public TemplateResponse update(Long id, TemplateRequest request) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla no encontrada: " + id));
        templateMapper.updateEntity(request, template, findLogoAsset(request.logoAssetId()));
        if (request.active()) {
            templateRepository.findAll().stream()
                    .filter(t -> !t.getId().equals(id))
                    .forEach(t -> t.setActive(false));
        }
        return templateMapper.toResponse(templateRepository.save(template));
    }

    @Override
    @Transactional
    public TemplateResponse activate(Long id) {
        Template template = findTemplate(id);
        templateRepository.findAll().forEach(t -> t.setActive(t.getId().equals(id)));
        return templateMapper.toResponse(template);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Template template = findTemplate(id);
        if (template.isSystemDefault()) {
            throw new ApiException("La plantilla por defecto no se puede eliminar");
        }
        if (template.isActive()) {
            throw new ApiException("No se puede eliminar la plantilla activa");
        }
        jdbcTemplate.update("DELETE FROM template_assets WHERE template_id = ?", id);
        templateVersionRepository.deleteAll(templateVersionRepository.findByTemplateIdOrderByCreatedAtDesc(id));
        templateRepository.delete(template);
    }

    @Override
    @Transactional
    public TemplateResponse uploadLogo(Long id, MultipartFile file) {
        Template template = findTemplate(id);
        validateLogoFile(file);
        AssetResponse response = assetStorageService.storeAsset(file, AssetType.LOGO, null);
        Asset asset = assetRepository.findById(response.id())
                .orElseThrow(() -> new ResourceNotFoundException("Asset no encontrado: " + response.id()));
        template.setLogoAsset(asset);
        template.setLogoPath("/api/v1/assets/" + asset.getId() + "/file");
        return templateMapper.toResponse(templateRepository.save(template));
    }

    private void validateLogoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("Archivo vacio");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean isAllowedImage = contentType.startsWith("image/")
                || filename.endsWith(".svg")
                || filename.endsWith(".svgz");

        if (!isAllowedImage) {
            throw new ApiException("El logo debe ser una imagen PNG, JPG, GIF, WEBP o SVG");
        }

        if (filename.endsWith(".svg") || contentType.equals("image/svg+xml")) {
            validateSvg(file);
        }
    }

    private void validateSvg(MultipartFile file) {
        try {
            String svg = new String(file.getBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (!svg.contains("<svg")) {
                throw new ApiException("El archivo SVG no parece valido");
            }
            if (svg.contains("<script") || svg.matches("(?s).*\\son[a-z]+\\s*=.*")) {
                throw new ApiException("El SVG no puede contener scripts ni manejadores de eventos");
            }
        } catch (IOException ex) {
            throw new ApiException("No se pudo validar el SVG", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateVersionResponse> findVersions(Long templateId) {
        findTemplate(templateId);
        return templateVersionRepository.findByTemplateIdOrderByCreatedAtDesc(templateId)
                .stream()
                .map(templateMapper::toVersionResponse)
                .toList();
    }

    @Override
    @Transactional
    public TemplateVersionResponse createVersion(Long templateId, TemplateVersionRequest request) {
        Template template = findTemplate(templateId);
        if (request.active()) {
            templateVersionRepository.findByTemplateIdOrderByCreatedAtDesc(templateId)
                    .forEach(version -> version.setActive(false));
        }
        TemplateVersion version = TemplateVersion.builder()
                .template(template)
                .versionNumber(request.versionNumber())
                .configJson(request.configJson())
                .active(request.active())
                .notes(request.notes())
                .build();
        return templateMapper.toVersionResponse(templateVersionRepository.save(version));
    }

    private Template findTemplate(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla no encontrada: " + id));
    }

    private Asset findLogoAsset(Long logoAssetId) {
        if (logoAssetId == null) {
            return null;
        }
        return assetRepository.findById(logoAssetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset no encontrado: " + logoAssetId));
    }
}
