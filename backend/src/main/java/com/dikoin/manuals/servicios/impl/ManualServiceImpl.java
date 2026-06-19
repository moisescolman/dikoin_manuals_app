package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.manual.*;
import com.dikoin.manuals.entidades.*;
import com.dikoin.manuals.enums.LanguageCode;
import com.dikoin.manuals.enums.ManualStatus;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.ManualMapper;
import com.dikoin.manuals.repositorios.*;
import com.dikoin.manuals.servicios.ManualCodeService;
import com.dikoin.manuals.servicios.ManualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ManualServiceImpl implements ManualService {

    private final ManualRepository manualRepository;
    private final ManualVersionRepository manualVersionRepository;
    private final ProductRepository productRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final ManualCodeService manualCodeService;
    private final ManualMapper manualMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ManualSummaryResponse> findAll(String search) {
        List<Manual> manuals = manualRepository.searchActive(search == null || search.isBlank() ? null : search);

        return manuals.stream()
                .map(manual -> manualMapper.toSummary(manual, findActiveVersion(manual.getId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ManualDetailResponse findById(Long id) {
        Manual manual = getManual(id);
        List<ManualVersion> versions = manualVersionRepository.findByManualIdOrderByCreatedAtDesc(id);
        return manualMapper.toDetail(manual, versions, findActiveVersion(id));
    }

    @Override
    @Transactional
    public ManualDetailResponse create(ManualCreateRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + request.productId()));
        DocumentType documentType = findDocumentType(request.documentTypeId());
        String code = resolveManualCode(request.code(), documentType, product, request.documentYear(), request.documentVersion(), request.languageCode());

        if (manualRepository.existsByCodeIgnoreCase(code)) {
            throw new ApiException("Ya existe un manual con el codigo " + code);
        }

        Manual manual = Manual.builder()
                .code(code)
                .title(request.title())
                .titleEs(request.titleEs() == null || request.titleEs().isBlank() ? request.title() : request.titleEs())
                .titleEn(request.titleEn())
                .category(request.category())
                .documentType(documentType)
                .documentYear(manualCodeService.twoDigits(request.documentYear()))
                .documentVersion(manualCodeService.twoDigits(request.documentVersion()))
                .languageCode(normalizeLanguage(request.languageCode()))
                .enabled(true)
                .product(product)
                .build();

        Manual saved = manualRepository.save(manual);
        createDefaultDraftVersion(saved);
        return findById(saved.getId());
    }

    @Override
    @Transactional
    public ManualDetailResponse update(Long id, ManualUpdateRequest request) {
        Manual manual = getManual(id);
        manual.setTitle(request.title());
        manual.setTitleEs(request.titleEs() == null || request.titleEs().isBlank() ? request.title() : request.titleEs());
        manual.setTitleEn(request.titleEn());
        manual.setCategory(request.category());
        DocumentType documentType = findDocumentType(request.documentTypeId());
        manual.setDocumentType(documentType);
        manual.setDocumentYear(manualCodeService.twoDigits(request.documentYear()));
        manual.setDocumentVersion(manualCodeService.twoDigits(request.documentVersion()));
        manual.setLanguageCode(normalizeLanguage(request.languageCode()));
        String generatedCode = manualCodeService.generate(documentType, manual.getProduct(), manual.getDocumentYear(), manual.getDocumentVersion(), manual.getLanguageCode());
        if (generatedCode != null && !generatedCode.equalsIgnoreCase(manual.getCode())) {
            manualRepository.findByCodeIgnoreCase(generatedCode)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new ApiException("Ya existe un manual con el codigo " + generatedCode);
                    });
            manual.setCode(generatedCode);
        }
        manualRepository.save(manual);
        return findById(id);
    }

    @Override
    @Transactional
    public ManualVersionResponse saveVersion(Long manualId, ManualVersionRequest request) {
        Manual manual = getManual(manualId);
        ManualVersion version = manualVersionRepository.findByManualIdAndActiveTrue(manualId)
                .filter(active -> active.getStatus() != ManualStatus.PUBLISHED)
                .orElseGet(() -> ManualVersion.builder()
                        .manual(manual)
                        .versionNumber(request.versionNumber())
                        .status(request.status() == null ? ManualStatus.DRAFT : request.status())
                        .active(false)
                        .build());

        version.setVersionNumber(request.versionNumber());
        version.setStatus(request.status() == null ? ManualStatus.DRAFT : request.status());
        version.setActive(request.active());
        version.setEsReady(request.esReady());
        version.setEnReady(request.enReady());
        version.setChangeNotes(request.changeNotes());
        version.getSections().clear();

        if (request.sections() != null) {
            request.sections().forEach(sectionRequest -> version.getSections().add(toSection(version, sectionRequest)));
        }

        if (request.active()) {
            deactivateOtherVersions(manualId, version.getId());
        }

        ManualVersion saved = manualVersionRepository.save(version);
        return manualMapper.toVersionResponse(saved, true);
    }

    @Override
    @Transactional
    public ManualVersionResponse publishVersion(Long manualId, Long versionId, PublishVersionRequest request) {
        getManual(manualId);
        ManualVersion version = manualVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version no encontrada: " + versionId));

        if (!version.getManual().getId().equals(manualId)) {
            throw new ApiException("La version no pertenece al manual indicado");
        }

        deactivateOtherVersions(manualId, versionId);
        version.setStatus(ManualStatus.PUBLISHED);
        version.setActive(true);
        version.setChangeNotes(request != null ? request.changeNotes() : version.getChangeNotes());
        version.setPublishedAt(LocalDateTime.now());
        return manualMapper.toVersionResponse(manualVersionRepository.save(version), true);
    }

    @Override
    @Transactional
    public ManualDetailResponse setEnabled(Long id, ManualEnabledRequest request) {
        Manual manual = getManual(id);
        manual.setEnabled(request.enabled());
        manualRepository.save(manual);
        return findById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Manual manual = getManual(id);
        manual.setEnabled(false);
        manual.setDeletedAt(LocalDateTime.now());
        manualRepository.save(manual);
    }

    private Manual getManual(Long id) {
        return manualRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manual no encontrado: " + id));
    }

    private ManualVersion findActiveVersion(Long manualId) {
        return manualVersionRepository.findByManualIdAndActiveTrue(manualId)
                .orElseGet(() -> manualVersionRepository.findByManualIdOrderByCreatedAtDesc(manualId)
                        .stream()
                        .findFirst()
                        .orElse(null));
    }

    private DocumentType findDocumentType(Long id) {
        if (id == null) {
            return null;
        }
        return documentTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo documental no encontrado: " + id));
    }

    private String resolveManualCode(String fallbackCode, DocumentType documentType, Product product, String year, String version, String languageCode) {
        String generated = manualCodeService.generate(documentType, product, year, version, languageCode);
        if (generated != null) {
            return generated;
        }
        if (fallbackCode == null || fallbackCode.isBlank()) {
            throw new ApiException("No se pudo generar el codigo del manual. Complete tipo documental, año, version e idioma.");
        }
        return fallbackCode.trim();
    }

    private String normalizeLanguage(String languageCode) {
        return languageCode == null || languageCode.isBlank() ? null : languageCode.trim().toUpperCase();
    }

    private ManualVersion createDefaultDraftVersion(Manual manual) {
        LanguageCode initialLanguage = parseLanguage(manual.getLanguageCode());
        ManualVersion version = ManualVersion.builder()
                .manual(manual)
                .versionNumber("0.1")
                .status(ManualStatus.DRAFT)
                .active(true)
                .esReady(initialLanguage == LanguageCode.ES)
                .enReady(initialLanguage == LanguageCode.EN)
                .changeNotes("Version inicial")
                .build();

        ManualSection section = ManualSection.builder()
                .manualVersion(version)
                .sortOrder(1)
                .sectionNumber("1")
                .titleEs("Introduccion")
                .titleEn("Introduction")
                .completionStatus("PENDING")
                .build();
        section.getBlocks().add(ManualBlock.builder()
                .section(section)
                .sortOrder(1)
                .blockType(com.dikoin.manuals.enums.BlockType.PARAGRAPH)
                .languageCode(initialLanguage)
                .contentJson("{\"type\":\"paragraph\",\"text\":\"Contenido inicial del manual.\"}")
                .build());
        version.getSections().add(section);
        return manualVersionRepository.save(version);
    }

    private LanguageCode parseLanguage(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return LanguageCode.ES;
        }
        try {
            return LanguageCode.valueOf(languageCode.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return LanguageCode.ES;
        }
    }

    private ManualSection toSection(ManualVersion version, ManualSectionRequest request) {
        ManualSection section = ManualSection.builder()
                .manualVersion(version)
                .sortOrder(request.sortOrder())
                .sectionNumber(request.sectionNumber())
                .titleEs(request.titleEs())
                .titleEn(request.titleEn())
                .completionStatus(request.completionStatus())
                .build();

        if (request.blocks() != null) {
            request.blocks().forEach(blockRequest -> section.getBlocks().add(toBlock(section, blockRequest)));
        }
        return section;
    }

    private ManualBlock toBlock(ManualSection section, ManualBlockRequest request) {
        return ManualBlock.builder()
                .section(section)
                .sortOrder(request.sortOrder())
                .blockType(request.blockType())
                .languageCode(request.languageCode())
                .contentJson(request.contentJson())
                .build();
    }

    private void deactivateOtherVersions(Long manualId, Long exceptVersionId) {
        manualVersionRepository.findByManualIdOrderByCreatedAtDesc(manualId).forEach(version -> {
            if (exceptVersionId == null || !version.getId().equals(exceptVersionId)) {
                version.setActive(false);
            }
        });
    }
}
