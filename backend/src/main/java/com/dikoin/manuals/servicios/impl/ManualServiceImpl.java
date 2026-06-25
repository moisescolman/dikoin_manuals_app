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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ManualServiceImpl implements ManualService {

    private final ManualRepository manualRepository;
    private final ManualVersionRepository manualVersionRepository;
    private final ProductRepository productRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final AssetRepository assetRepository;
    private final ReusableBlockRepository reusableBlockRepository;
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

        String initialTitleEs = request.titleEs() == null || request.titleEs().isBlank() ? request.title() : request.titleEs();
        Manual manual = Manual.builder()
                .code(code)
                .title(request.title())
                .titleEs(initialTitleEs)
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
        mergeSections(version, request.sections() == null ? List.of() : request.sections());
        recalculateSectionNumbers(version);

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
                .titleEn("Introduccion")
                .completionStatus("PENDING")
                .build();
        String initialContent = "{\"type\":\"paragraph\",\"text\":\"Contenido inicial del manual.\"}";
        section.getBlocks().add(ManualBlock.builder()
                .section(section)
                .sortOrder(1)
                .blockType(com.dikoin.manuals.enums.BlockType.PARAGRAPH)
                .languageCode(LanguageCode.ES)
                .contentJson(initialContent)
                .build());
        section.getBlocks().add(ManualBlock.builder()
                .section(section)
                .sortOrder(1)
                .blockType(com.dikoin.manuals.enums.BlockType.PARAGRAPH)
                .languageCode(LanguageCode.EN)
                .contentJson(initialContent)
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

    private void mergeSections(ManualVersion version, List<ManualSectionRequest> requests) {
        Map<Long, ManualSection> existingById = version.getSections().stream()
                .filter(section -> section.getId() != null)
                .collect(java.util.stream.Collectors.toMap(ManualSection::getId, section -> section));
        Map<Long, ManualSection> requestedById = new HashMap<>();
        Map<ManualSection, ManualSectionRequest> requestBySection = new HashMap<>();
        List<ManualSection> merged = new ArrayList<>();

        for (ManualSectionRequest request : requests) {
            ManualSection section = request.id() == null ? null : existingById.get(request.id());
            if (request.id() != null && section == null) {
                throw new ApiException("La seccion " + request.id() + " no pertenece a la version editable");
            }
            if (section == null) {
                section = ManualSection.builder().manualVersion(version).build();
            }
            section.setManualVersion(version);
            section.setSortOrder(request.sortOrder());
            section.setSectionNumber(request.sectionNumber());
            section.setLevel(request.level() == null ? 1 : Math.max(1, request.level()));
            section.setTitleEs(request.titleEs());
            section.setTitleEn(request.titleEn());
            section.setCompletionStatus(request.completionStatus());
            section.setVisible(request.visible() == null || request.visible());
            mergeBlocks(section, request.blocks() == null ? List.of() : request.blocks());
            merged.add(section);
            requestBySection.put(section, request);
            if (request.id() != null) {
                requestedById.put(request.id(), section);
            }
        }

        for (ManualSection section : merged) {
            Long parentId = requestBySection.get(section).parentSectionId();
            if (parentId == null) {
                section.setParentSection(null);
                continue;
            }
            ManualSection parent = requestedById.get(parentId);
            if (parent == null || parent == section) {
                throw new ApiException("La seccion padre " + parentId + " no es valida para esta version");
            }
            section.setParentSection(parent);
        }

        version.getSections().removeIf(section -> !merged.contains(section));
        for (ManualSection section : merged) {
            if (!version.getSections().contains(section)) {
                version.getSections().add(section);
            }
        }
    }

    private void mergeBlocks(ManualSection section, List<ManualBlockRequest> requests) {
        Map<Long, ManualBlock> existingById = section.getBlocks().stream()
                .filter(block -> block.getId() != null)
                .collect(java.util.stream.Collectors.toMap(ManualBlock::getId, block -> block));
        List<ManualBlock> merged = new ArrayList<>();

        for (ManualBlockRequest request : requests) {
            ManualBlock block = request.id() == null ? null : existingById.get(request.id());
            if (request.id() != null && block == null) {
                throw new ApiException("El bloque " + request.id() + " no pertenece a la seccion indicada");
            }
            if (block == null) {
                block = ManualBlock.builder().section(section).build();
            }
            validateBlock(request);
            block.setSection(section);
            block.setSortOrder(request.sortOrder());
            block.setBlockType(request.blockType());
            block.setLanguageCode(request.languageCode());
            block.setContentJson(request.contentJson());
            block.setPlainText(request.plainText());
            block.setAsset(request.assetId() == null ? null : assetRepository.findById(request.assetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Asset no encontrado: " + request.assetId())));
            block.setReusableBlock(request.reusableBlockId() == null ? null : reusableBlockRepository.findById(request.reusableBlockId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bloque reutilizable no encontrado: " + request.reusableBlockId())));
            merged.add(block);
        }

        List<ManualBlock> bilingual = normalizeBilingualBlocks(section, merged);
        section.getBlocks().removeIf(block -> !bilingual.contains(block));
        for (ManualBlock block : bilingual) {
            if (!section.getBlocks().contains(block)) {
                section.getBlocks().add(block);
            }
        }
    }

    private List<ManualBlock> normalizeBilingualBlocks(ManualSection section, List<ManualBlock> blocks) {
        List<ManualBlock> spanish = blocks.stream()
                .filter(block -> block.getLanguageCode() == LanguageCode.ES)
                .sorted(Comparator.comparing(ManualBlock::getSortOrder))
                .toList();
        List<ManualBlock> english = blocks.stream()
                .filter(block -> block.getLanguageCode() == LanguageCode.EN)
                .sorted(Comparator.comparing(ManualBlock::getSortOrder))
                .toList();
        int count = Math.max(spanish.size(), english.size());
        List<ManualBlock> normalized = new ArrayList<>(count * 2);
        for (int index = 0; index < count; index++) {
            ManualBlock es = index < spanish.size()
                    ? spanish.get(index)
                    : cloneBlock(section, english.get(index), LanguageCode.ES);
            ManualBlock en = index < english.size()
                    ? english.get(index)
                    : cloneBlock(section, spanish.get(index), LanguageCode.EN);
            en.setBlockType(es.getBlockType());
            if (es.getBlockType() == com.dikoin.manuals.enums.BlockType.IMAGE) {
                en.setAsset(es.getAsset());
            }
            es.setSortOrder(index + 1);
            en.setSortOrder(index + 1);
            normalized.add(es);
            normalized.add(en);
        }
        return normalized;
    }

    private ManualBlock cloneBlock(ManualSection section, ManualBlock source, LanguageCode language) {
        return ManualBlock.builder()
                .section(section)
                .sortOrder(source.getSortOrder())
                .blockType(source.getBlockType())
                .languageCode(language)
                .contentJson(source.getContentJson())
                .plainText(source.getPlainText())
                .asset(source.getAsset())
                .reusableBlock(source.getReusableBlock())
                .build();
    }

    private void validateBlock(ManualBlockRequest request) {
        if (request.blockType() == com.dikoin.manuals.enums.BlockType.FORMULA
                && (request.contentJson() == null
                || request.contentJson().isBlank()
                || !request.contentJson().contains("\"latex\""))) {
            throw new ApiException("La ecuacion debe contener una expresion LaTeX");
        }
    }

    private void recalculateSectionNumbers(ManualVersion version) {
        Map<ManualSection, List<ManualSection>> childrenByParent = new HashMap<>();
        List<ManualSection> roots = new ArrayList<>();
        Set<ManualSection> sections = new HashSet<>(version.getSections());

        for (ManualSection section : version.getSections()) {
            ManualSection parent = section.getParentSection();
            if (parent == null || !sections.contains(parent)) {
                section.setParentSection(null);
                roots.add(section);
            } else {
                childrenByParent.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(section);
            }
        }

        roots.sort(Comparator.comparing(ManualSection::getSortOrder));
        for (int index = 0; index < roots.size(); index++) {
            numberSection(roots.get(index), String.valueOf(index + 1), 1, childrenByParent, new HashSet<>());
        }
    }

    private void numberSection(
            ManualSection section,
            String number,
            int level,
            Map<ManualSection, List<ManualSection>> childrenByParent,
            Set<ManualSection> path
    ) {
        if (!path.add(section)) {
            throw new ApiException("Se ha detectado un ciclo en la jerarquia de secciones");
        }
        section.setSectionNumber(number);
        section.setLevel(level);
        List<ManualSection> children = childrenByParent.getOrDefault(section, List.of()).stream()
                .sorted(Comparator.comparing(ManualSection::getSortOrder))
                .toList();
        for (int index = 0; index < children.size(); index++) {
            numberSection(children.get(index), number + "." + (index + 1), level + 1, childrenByParent, new HashSet<>(path));
        }
    }

    private void deactivateOtherVersions(Long manualId, Long exceptVersionId) {
        manualVersionRepository.findByManualIdOrderByCreatedAtDesc(manualId).forEach(version -> {
            if (exceptVersionId == null || !version.getId().equals(exceptVersionId)) {
                version.setActive(false);
            }
        });
    }
}
