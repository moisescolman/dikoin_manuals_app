package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.manual.*;
import com.dikoin.manuals.entidades.Asset;
import com.dikoin.manuals.entidades.ManualBlock;
import com.dikoin.manuals.entidades.ManualSection;
import com.dikoin.manuals.entidades.ManualVersion;
import com.dikoin.manuals.entidades.ReusableBlock;
import com.dikoin.manuals.entidades.ReusableFragment;
import com.dikoin.manuals.enums.BlockType;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.ManualMapper;
import com.dikoin.manuals.repositorios.*;
import com.dikoin.manuals.servicios.ManualContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ManualContentServiceImpl implements ManualContentService {

    private final ManualVersionRepository manualVersionRepository;
    private final ManualSectionRepository manualSectionRepository;
    private final ManualBlockRepository manualBlockRepository;
    private final AssetRepository assetRepository;
    private final ReusableBlockRepository reusableBlockRepository;
    private final ReusableFragmentRepository reusableFragmentRepository;
    private final ManualMapper manualMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ManualSectionResponse> findSections(Long versionId) {
        findVersion(versionId);
        return orderedSections(versionId).stream().map(manualMapper::toSectionResponse).toList();
    }

    @Override
    @Transactional
    public ManualSectionResponse createSection(Long versionId, Long parentSectionId, ManualSectionRequest request) {
        ManualSection parent = parentSectionId == null ? null : findSection(parentSectionId);
        ManualVersion version = parent == null ? findVersion(versionId) : parent.getManualVersion();
        int sortOrder = request.sortOrder() == null || request.sortOrder() < 1
                ? nextSectionSort(version.getId(), parentSectionId)
                : request.sortOrder();

        ManualSection section = ManualSection.builder()
                .manualVersion(version)
                .parentSection(parent)
                .level(parent == null ? 1 : parent.getLevel() + 1)
                .sortOrder(sortOrder)
                .sectionNumber(request.sectionNumber())
                .titleEs(request.titleEs())
                .titleEn(request.titleEn())
                .completionStatus(request.completionStatus())
                .visible(request.visible() == null || request.visible())
                .build();
        ManualSection saved = manualSectionRepository.save(section);
        recalculate(version);
        return manualMapper.toSectionResponse(saved);
    }

    @Override
    @Transactional
    public ManualSectionResponse updateSection(Long sectionId, ManualSectionPatchRequest request) {
        ManualSection section = findSection(sectionId);
        if (request.titleEs() != null) {
            if (request.titleEs().isBlank()) {
                throw new ApiException("El titulo de la seccion no puede estar vacio");
            }
            section.setTitleEs(request.titleEs().trim());
        }
        if (request.titleEn() != null) {
            section.setTitleEn(request.titleEn());
        }
        if (request.completionStatus() != null) {
            section.setCompletionStatus(request.completionStatus());
        }
        if (request.visible() != null) {
            section.setVisible(request.visible());
        }
        return manualMapper.toSectionResponse(manualSectionRepository.save(section));
    }

    @Override
    @Transactional
    public List<ManualSectionResponse> reorderSections(Long versionId, ReorderRequest request) {
        ManualVersion version = findVersion(versionId);
        Map<Long, ManualSection> sections = manualSectionRepository.findByManualVersionIdOrderBySortOrderAsc(versionId)
                .stream().collect(java.util.stream.Collectors.toMap(ManualSection::getId, section -> section));
        for (OrderItemRequest item : request.items()) {
            ManualSection section = sections.get(item.id());
            if (section == null) {
                throw new ApiException("La seccion " + item.id() + " no pertenece a la version");
            }
            section.setSortOrder(item.sortOrder());
        }
        manualSectionRepository.saveAll(sections.values());
        recalculate(version);
        return findSections(versionId);
    }

    @Override
    @Transactional
    public void deleteSection(Long sectionId, boolean force) {
        ManualSection section = findSection(sectionId);
        List<ManualSection> descendants = descendantsOf(section);
        boolean hasContent = !section.getBlocks().isEmpty()
                || descendants.stream().anyMatch(child -> !child.getBlocks().isEmpty());
        if ((!descendants.isEmpty() || hasContent) && !force) {
            throw new ApiException("La seccion contiene bloques o subsecciones. Confirme la eliminacion con force=true");
        }
        List<ManualSection> toDelete = new ArrayList<>(descendants);
        toDelete.add(section);
        toDelete.sort(Comparator.comparing(ManualSection::getLevel).reversed());
        for (ManualSection current : toDelete) {
            manualSectionRepository.delete(current);
            manualSectionRepository.flush();
        }
        recalculate(section.getManualVersion());
    }

    @Override
    @Transactional
    public List<ManualSectionResponse> recalculateNumbers(Long versionId) {
        ManualVersion version = findVersion(versionId);
        recalculate(version);
        return findSections(versionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualBlockResponse> findBlocks(Long sectionId) {
        findSection(sectionId);
        return manualBlockRepository.findBySectionIdOrderBySortOrderAsc(sectionId).stream()
                .map(manualMapper::toBlockResponse)
                .toList();
    }

    @Override
    @Transactional
    public ManualBlockResponse createBlock(Long sectionId, ManualBlockRequest request) {
        ManualSection section = findSection(sectionId);
        ManualBlock block = ManualBlock.builder().section(section).build();
        applyBlock(block, request);
        if (request.sortOrder() == null || request.sortOrder() < 1) {
            block.setSortOrder(manualBlockRepository.findBySectionIdOrderBySortOrderAsc(sectionId).size() + 1);
        }
        return manualMapper.toBlockResponse(manualBlockRepository.save(block));
    }

    @Override
    @Transactional
    public ManualBlockResponse updateBlock(Long blockId, ManualBlockRequest request) {
        ManualBlock block = findBlock(blockId);
        applyBlock(block, request);
        return manualMapper.toBlockResponse(manualBlockRepository.save(block));
    }

    @Override
    @Transactional
    public void deleteBlock(Long blockId) {
        ManualBlock block = findBlock(blockId);
        Long sectionId = block.getSection().getId();
        manualBlockRepository.delete(block);
        normalizeBlockOrder(sectionId);
    }

    @Override
    @Transactional
    public ManualBlockResponse duplicateBlock(Long blockId) {
        ManualBlock source = findBlock(blockId);
        shiftBlockOrder(source.getSection().getId(), source.getSortOrder() + 1, 1);
        ManualBlock copy = ManualBlock.builder()
                .section(source.getSection())
                .sortOrder(source.getSortOrder() + 1)
                .blockType(source.getBlockType())
                .languageCode(source.getLanguageCode())
                .contentJson(source.getContentJson())
                .plainText(source.getPlainText())
                .asset(source.getAsset())
                .reusableBlock(source.getReusableBlock())
                .reusableFragment(source.getReusableFragment())
                .build();
        return manualMapper.toBlockResponse(manualBlockRepository.save(copy));
    }

    @Override
    @Transactional
    public ManualBlockResponse moveBlock(Long blockId, MoveBlockRequest request) {
        ManualBlock block = findBlock(blockId);
        ManualSection source = block.getSection();
        ManualSection target = findSection(request.targetSectionId());
        if (!source.getManualVersion().getId().equals(target.getManualVersion().getId())) {
            throw new ApiException("Solo se pueden mover bloques dentro de la misma version");
        }
        int targetOrder = resolveInsertionOrder(target.getId(), request.insertAfterBlockId());
        shiftBlockOrder(target.getId(), targetOrder, 1);
        block.setSection(target);
        block.setSortOrder(targetOrder);
        ManualBlock saved = manualBlockRepository.save(block);
        normalizeBlockOrder(source.getId());
        normalizeBlockOrder(target.getId());
        return manualMapper.toBlockResponse(saved);
    }

    @Override
    @Transactional
    public List<ManualBlockResponse> reorderBlocks(Long sectionId, ReorderRequest request) {
        findSection(sectionId);
        Map<Long, ManualBlock> blocks = manualBlockRepository.findBySectionIdOrderBySortOrderAsc(sectionId)
                .stream().collect(java.util.stream.Collectors.toMap(ManualBlock::getId, block -> block));
        for (OrderItemRequest item : request.items()) {
            ManualBlock block = blocks.get(item.id());
            if (block == null) {
                throw new ApiException("El bloque " + item.id() + " no pertenece a la seccion");
            }
            block.setSortOrder(item.sortOrder());
        }
        manualBlockRepository.saveAll(blocks.values());
        normalizeBlockOrder(sectionId);
        return findBlocks(sectionId);
    }

    private void applyBlock(ManualBlock block, ManualBlockRequest request) {
        validateBlock(request.blockType(), request.contentJson());
        block.setSortOrder(request.sortOrder());
        block.setBlockType(request.blockType());
        block.setLanguageCode(request.languageCode());
        block.setContentJson(request.contentJson());
        block.setPlainText(request.plainText());
        block.setAsset(findAsset(request.assetId()));
        block.setReusableBlock(findReusableBlock(request.reusableBlockId()));
        block.setReusableFragment(findReusableFragment(request.reusableFragmentId()));
    }

    private void validateBlock(BlockType type, String contentJson) {
        if (type == BlockType.FORMULA
                && (contentJson == null || contentJson.isBlank() || !contentJson.contains("\"latex\""))) {
            throw new ApiException("La ecuacion debe contener una expresion LaTeX");
        }
    }

    private Asset findAsset(Long id) {
        return id == null ? null : assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset no encontrado: " + id));
    }

    private ReusableBlock findReusableBlock(Long id) {
        return id == null ? null : reusableBlockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloque reutilizable no encontrado: " + id));
    }

    private ReusableFragment findReusableFragment(Long id) {
        return id == null ? null : reusableFragmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fragmento no encontrado: " + id));
    }

    private int resolveInsertionOrder(Long sectionId, Long insertAfterBlockId) {
        List<ManualBlock> blocks = manualBlockRepository.findBySectionIdOrderBySortOrderAsc(sectionId);
        if (insertAfterBlockId == null) {
            return blocks.size() + 1;
        }
        ManualBlock anchor = findBlock(insertAfterBlockId);
        if (!anchor.getSection().getId().equals(sectionId)) {
            throw new ApiException("El bloque de referencia no pertenece a la seccion destino");
        }
        return anchor.getSortOrder() + 1;
    }

    private void shiftBlockOrder(Long sectionId, int fromOrder, int delta) {
        List<ManualBlock> blocks = manualBlockRepository.findBySectionIdOrderBySortOrderAsc(sectionId);
        blocks.stream()
                .filter(block -> block.getSortOrder() >= fromOrder)
                .forEach(block -> block.setSortOrder(block.getSortOrder() + delta));
        manualBlockRepository.saveAll(blocks);
    }

    private void normalizeBlockOrder(Long sectionId) {
        List<ManualBlock> blocks = manualBlockRepository.findBySectionIdOrderBySortOrderAsc(sectionId);
        for (int index = 0; index < blocks.size(); index++) {
            blocks.get(index).setSortOrder(index + 1);
        }
        manualBlockRepository.saveAll(blocks);
    }

    private int nextSectionSort(Long versionId, Long parentSectionId) {
        List<ManualSection> siblings = parentSectionId == null
                ? manualSectionRepository.findByManualVersionIdOrderBySortOrderAsc(versionId).stream()
                .filter(section -> section.getParentSection() == null)
                .toList()
                : manualSectionRepository.findByParentSectionIdOrderBySortOrderAsc(parentSectionId);
        return siblings.size() + 1;
    }

    private void recalculate(ManualVersion version) {
        List<ManualSection> sections = manualSectionRepository.findByManualVersionIdOrderBySortOrderAsc(version.getId());
        Map<ManualSection, List<ManualSection>> children = new HashMap<>();
        List<ManualSection> roots = new ArrayList<>();
        Set<ManualSection> all = new HashSet<>(sections);
        for (ManualSection section : sections) {
            if (section.getParentSection() == null || !all.contains(section.getParentSection())) {
                section.setParentSection(null);
                roots.add(section);
            } else {
                children.computeIfAbsent(section.getParentSection(), ignored -> new ArrayList<>()).add(section);
            }
        }
        roots.sort(Comparator.comparing(ManualSection::getSortOrder));
        for (int index = 0; index < roots.size(); index++) {
            numberSection(roots.get(index), String.valueOf(index + 1), 1, children, new HashSet<>());
        }
        manualSectionRepository.saveAll(sections);
    }

    private void numberSection(
            ManualSection section,
            String number,
            int level,
            Map<ManualSection, List<ManualSection>> children,
            Set<ManualSection> path
    ) {
        if (!path.add(section)) {
            throw new ApiException("Se ha detectado un ciclo en la jerarquia de secciones");
        }
        section.setSectionNumber(number);
        section.setLevel(level);
        List<ManualSection> nested = children.getOrDefault(section, List.of()).stream()
                .sorted(Comparator.comparing(ManualSection::getSortOrder))
                .toList();
        for (int index = 0; index < nested.size(); index++) {
            numberSection(nested.get(index), number + "." + (index + 1), level + 1, children, new HashSet<>(path));
        }
    }

    private List<ManualSection> descendantsOf(ManualSection root) {
        List<ManualSection> all = manualSectionRepository.findByManualVersionIdOrderBySortOrderAsc(root.getManualVersion().getId());
        List<ManualSection> result = new ArrayList<>();
        collectDescendants(root, all, result);
        return result;
    }

    private void collectDescendants(ManualSection parent, List<ManualSection> all, List<ManualSection> result) {
        for (ManualSection section : all) {
            if (section.getParentSection() != null && section.getParentSection().getId().equals(parent.getId())) {
                result.add(section);
                collectDescendants(section, all, result);
            }
        }
    }

    private List<ManualSection> orderedSections(Long versionId) {
        return manualSectionRepository.findByManualVersionIdOrderBySortOrderAsc(versionId).stream()
                .sorted(Comparator.comparing(ManualSection::getSectionNumber, this::compareSectionNumbers))
                .toList();
    }

    private int compareSectionNumbers(String left, String right) {
        String[] a = String.valueOf(left).split("\\.");
        String[] b = String.valueOf(right).split("\\.");
        for (int index = 0; index < Math.max(a.length, b.length); index++) {
            int av = index < a.length ? parseNumber(a[index]) : 0;
            int bv = index < b.length ? parseNumber(b[index]) : 0;
            if (av != bv) {
                return Integer.compare(av, bv);
            }
        }
        return Integer.compare(a.length, b.length);
    }

    private int parseNumber(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private ManualVersion findVersion(Long id) {
        return manualVersionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Version no encontrada: " + id));
    }

    private ManualSection findSection(Long id) {
        return manualSectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seccion no encontrada: " + id));
    }

    private ManualBlock findBlock(Long id) {
        return manualBlockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloque no encontrado: " + id));
    }
}
