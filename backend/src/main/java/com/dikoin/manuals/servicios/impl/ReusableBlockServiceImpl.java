package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.reusableblock.*;
import com.dikoin.manuals.entidades.Asset;
import com.dikoin.manuals.entidades.ManualBlock;
import com.dikoin.manuals.entidades.ManualSection;
import com.dikoin.manuals.entidades.ReusableBlock;
import com.dikoin.manuals.enums.BlockType;
import com.dikoin.manuals.enums.LanguageCode;
import com.dikoin.manuals.enums.ReusableType;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.ReusableBlockMapper;
import com.dikoin.manuals.repositorios.ManualBlockRepository;
import com.dikoin.manuals.repositorios.ManualSectionRepository;
import com.dikoin.manuals.repositorios.AssetRepository;
import com.dikoin.manuals.repositorios.ReusableBlockRepository;
import com.dikoin.manuals.servicios.ReusableBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReusableBlockServiceImpl implements ReusableBlockService {

    private final ReusableBlockRepository reusableBlockRepository;
    private final ManualBlockRepository manualBlockRepository;
    private final ManualSectionRepository manualSectionRepository;
    private final AssetRepository assetRepository;
    private final ReusableBlockMapper reusableBlockMapper;
    private final com.dikoin.manuals.mappers.ManualMapper manualMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ReusableBlockResponse> findAll(boolean includeInactive) {
        List<ReusableBlock> blocks = includeInactive
                ? reusableBlockRepository.findAllByOrderByUpdatedAtDesc()
                : reusableBlockRepository.findByActiveTrueOrderByUpdatedAtDesc();
        return blocks.stream().map(reusableBlockMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReusableBlockResponse findById(Long id) {
        return reusableBlockMapper.toResponse(findBlock(id));
    }

    @Override
    @Transactional
    public ReusableBlockResponse create(ReusableBlockRequest request) {
        if (reusableBlockRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ApiException("Ya existe un bloque con codigo " + request.code());
        }
        ReusableBlock block = ReusableBlock.builder().build();
        applyRequest(request, block);
        return reusableBlockMapper.toResponse(reusableBlockRepository.save(block));
    }

    @Override
    @Transactional
    public ReusableBlockResponse createFragment(CreateReusableFragmentRequest request) {
        List<SnapshotBlock> snapshots = snapshotBlocks(request);
        if (snapshots.isEmpty()) {
            throw new ApiException("No se puede guardar un fragmento sin bloques");
        }
        ReusableBlock fragment = ReusableBlock.builder()
                .code(nextFragmentCode())
                .title(request.name().trim())
                .description(request.description())
                .reusableType(ReusableType.FRAGMENT)
                .contentJson(fragmentJson(snapshots))
                .active(request.isReusable())
                .build();
        return reusableBlockMapper.toResponse(reusableBlockRepository.save(fragment));
    }

    @Override
    @Transactional
    public ReusableFragmentInsertResponse insertFragment(Long id, InsertReusableFragmentRequest request) {
        ReusableBlock fragment = findBlock(id);
        if (fragment.getReusableType() != ReusableType.FRAGMENT) {
            throw new ApiException("El contenido seleccionado no es un fragmento compuesto");
        }
        if (request.mode() != null && !request.mode().isBlank() && !"COPY".equalsIgnoreCase(request.mode())) {
            throw new ApiException("Solo esta disponible el modo COPY");
        }
        ManualSection target = manualSectionRepository.findById(request.targetSectionId())
                .orElseThrow(() -> new ResourceNotFoundException("Seccion no encontrada: " + request.targetSectionId()));
        List<SnapshotBlock> snapshots = parseFragment(fragment.getContentJson());
        if (snapshots.isEmpty()) {
            throw new ApiException("El fragmento no contiene bloques");
        }

        List<ManualBlock> existing = manualBlockRepository.findBySectionIdOrderBySortOrderAsc(target.getId());
        int insertionOrder = existing.size() + 1;
        if (request.insertAfterBlockId() != null) {
            ManualBlock anchor = manualBlockRepository.findById(request.insertAfterBlockId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bloque no encontrado: " + request.insertAfterBlockId()));
            if (!anchor.getSection().getId().equals(target.getId())) {
                throw new ApiException("El bloque de referencia no pertenece a la seccion destino");
            }
            insertionOrder = anchor.getSortOrder() + 1;
        }
        final int firstOrder = insertionOrder;
        existing.stream()
                .filter(block -> block.getSortOrder() >= firstOrder)
                .forEach(block -> block.setSortOrder(block.getSortOrder() + snapshots.size()));
        manualBlockRepository.saveAll(existing);

        List<ManualBlock> inserted = new ArrayList<>();
        for (int index = 0; index < snapshots.size(); index++) {
            SnapshotBlock snapshot = snapshots.get(index);
            ManualBlock copy = ManualBlock.builder()
                    .section(target)
                    .sortOrder(insertionOrder + index)
                    .blockType(snapshot.blockType())
                    .languageCode(snapshot.languageCode())
                    .contentJson(snapshot.contentJson())
                    .plainText(snapshot.plainText())
                    .asset(findAsset(snapshot.assetId()))
                    .reusableBlock(fragment)
                    .build();
            inserted.add(copy);
        }
        List<ManualBlock> saved = manualBlockRepository.saveAll(inserted);
        return new ReusableFragmentInsertResponse(
                fragment.getId(),
                target.getId(),
                saved.stream().map(manualMapper::toBlockResponse).toList()
        );
    }

    @Override
    @Transactional
    public ReusableBlockResponse update(Long id, ReusableBlockRequest request) {
        ReusableBlock block = findBlock(id);
        applyRequest(request, block);
        return reusableBlockMapper.toResponse(reusableBlockRepository.save(block));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReusableBlockUsageResponse> findUsages(Long id) {
        findBlock(id);
        List<ManualBlock> relationalUsages = manualBlockRepository.findByReusableBlockId(id);
        String token = "\"reusableBlockId\":" + id;
        Map<Long, ManualBlock> usages = new HashMap<>();
        relationalUsages.forEach(block -> usages.put(block.getId(), block));
        manualBlockRepository.findByContentJsonContaining(token).forEach(block -> usages.put(block.getId(), block));
        return usages.values().stream()
                .filter(block -> block.getSection().getManualVersion().getManual().getDeletedAt() == null)
                .map(reusableBlockMapper::toUsageResponse)
                .toList();
    }

    private void applyRequest(ReusableBlockRequest request, ReusableBlock block) {
        block.setCode(request.code());
        block.setTitle(request.title());
        block.setDescription(request.description());
        if (request.reusableType() != null) {
            block.setReusableType(request.reusableType());
        } else if (block.getReusableType() == null) {
            block.setReusableType(ReusableType.SINGLE_BLOCK);
        }
        block.setProductCategory(request.productCategory());
        block.setProductCodes(request.productCodes());
        block.setContentJson(request.contentJson());
        block.setActive(request.active());
    }

    private ReusableBlock findBlock(Long id) {
        return reusableBlockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloque reutilizable no encontrado: " + id));
    }

    private List<SnapshotBlock> snapshotBlocks(CreateReusableFragmentRequest request) {
        if (request.blocks() != null && !request.blocks().isEmpty()) {
            return request.blocks().stream()
                    .sorted(Comparator.comparing(block -> block.sortOrder() == null ? Integer.MAX_VALUE : block.sortOrder()))
                    .map(block -> {
                        validateEquation(block.blockType(), block.contentJson());
                        return new SnapshotBlock(
                                block.blockType(),
                                block.languageCode(),
                                block.contentJson(),
                                block.plainText(),
                                block.assetId(),
                                block.sortOrder()
                        );
                    })
                    .toList();
        }
        if (request.blockIds() == null || request.blockIds().isEmpty()) {
            return List.of();
        }
        List<ManualBlock> blocks = manualBlockRepository.findByIdIn(request.blockIds());
        if (blocks.size() != request.blockIds().stream().distinct().count()) {
            throw new ResourceNotFoundException("Uno o varios bloques seleccionados no existen");
        }
        if (request.sourceSectionId() != null && blocks.stream()
                .anyMatch(block -> !block.getSection().getId().equals(request.sourceSectionId()))) {
            throw new ApiException("Todos los bloques deben pertenecer a la seccion de origen");
        }
        return blocks.stream()
                .sorted(Comparator.comparing(ManualBlock::getSortOrder))
                .map(block -> new SnapshotBlock(
                        block.getBlockType(),
                        block.getLanguageCode(),
                        block.getContentJson(),
                        block.getPlainText(),
                        block.getAsset() != null ? block.getAsset().getId() : null,
                        block.getSortOrder()
                ))
                .toList();
    }

    private String fragmentJson(List<SnapshotBlock> snapshots) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "FRAGMENT");
        ArrayNode blocks = root.putArray("blocks");
        for (SnapshotBlock snapshot : snapshots) {
            ObjectNode node = blocks.addObject();
            node.put("blockType", snapshot.blockType().name());
            node.put("languageCode", snapshot.languageCode().name());
            try {
                node.set("contentJson", objectMapper.readTree(snapshot.contentJson()));
            } catch (Exception exception) {
                node.put("contentJson", snapshot.contentJson());
            }
            if (snapshot.plainText() != null) {
                node.put("plainText", snapshot.plainText());
            }
            if (snapshot.assetId() != null) {
                node.put("assetId", snapshot.assetId());
            }
            node.put("sortOrder", snapshot.sortOrder() == null ? blocks.size() : snapshot.sortOrder());
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new ApiException("No se pudo serializar el fragmento", exception);
        }
    }

    private List<SnapshotBlock> parseFragment(String contentJson) {
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            JsonNode blocks = root.path("blocks");
            if (!blocks.isArray()) {
                return List.of();
            }
            List<SnapshotBlock> snapshots = new ArrayList<>();
            for (JsonNode node : blocks) {
                JsonNode content = node.path("contentJson");
                String serializedContent = content.isTextual() ? content.asText() : objectMapper.writeValueAsString(content);
                BlockType blockType = BlockType.valueOf(node.path("blockType").asText());
                validateEquation(blockType, serializedContent);
                snapshots.add(new SnapshotBlock(
                        blockType,
                        LanguageCode.valueOf(node.path("languageCode").asText("ES")),
                        serializedContent,
                        node.path("plainText").isMissingNode() ? null : node.path("plainText").asText(null),
                        node.path("assetId").isNumber() ? node.path("assetId").asLong() : null,
                        node.path("sortOrder").asInt(snapshots.size() + 1)
                ));
            }
            return snapshots;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException("El JSON del fragmento no es valido", exception);
        }
    }

    private void validateEquation(BlockType type, String contentJson) {
        if (type == BlockType.FORMULA
                && (contentJson == null || contentJson.isBlank() || !contentJson.contains("\"latex\""))) {
            throw new ApiException("La ecuacion debe contener una expresion LaTeX");
        }
    }

    private Asset findAsset(Long id) {
        return id == null ? null : assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset no encontrado: " + id));
    }

    private String nextFragmentCode() {
        String base = "FRG-" + Instant.now().toEpochMilli();
        String code = base;
        int suffix = 1;
        while (reusableBlockRepository.existsByCodeIgnoreCase(code)) {
            code = base + "-" + suffix++;
        }
        return code;
    }

    private record SnapshotBlock(
            BlockType blockType,
            LanguageCode languageCode,
            String contentJson,
            String plainText,
            Long assetId,
            Integer sortOrder
    ) {
    }
}
