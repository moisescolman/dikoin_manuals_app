package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.reusableblock.*;
import com.dikoin.manuals.entidades.Asset;
import com.dikoin.manuals.entidades.ManualBlock;
import com.dikoin.manuals.entidades.ManualSection;
import com.dikoin.manuals.entidades.Product;
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
import com.dikoin.manuals.repositorios.ProductRepository;
import com.dikoin.manuals.repositorios.ReusableBlockRepository;
import com.dikoin.manuals.servicios.ReusableBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReusableBlockServiceImpl implements ReusableBlockService {

    private static final Pattern CODE_NUMBER = Pattern.compile("^[A-Z]+-(\\d+)$");
    private final ReusableBlockRepository reusableBlockRepository;
    private final ManualBlockRepository manualBlockRepository;
    private final ManualSectionRepository manualSectionRepository;
    private final AssetRepository assetRepository;
    private final ProductRepository productRepository;
    private final ReusableBlockMapper reusableBlockMapper;
    private final com.dikoin.manuals.mappers.ManualMapper manualMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ReusableBlockResponse> findAll(boolean includeInactive, ReusableType type, String search) {
        if (type == ReusableType.FRAGMENT) {
            return List.of();
        }
        ReusableType resolvedType = type == null ? ReusableType.SINGLE_BLOCK : type;
        List<ReusableBlock> blocks;
        blocks = includeInactive
                ? reusableBlockRepository.findByReusableTypeOrderByUpdatedAtDesc(resolvedType)
                : reusableBlockRepository.findByReusableTypeAndActiveTrueOrderByUpdatedAtDesc(resolvedType);
        String query = normalize(search);
        if (query != null) {
            Map<String, Product> productsByCode = productRepository.findAllWithTaxonomy().stream()
                    .collect(Collectors.toMap(product -> product.getCode().toUpperCase(), product -> product, (a, b) -> a));
            blocks = blocks.stream()
                    .filter(block -> matches(block, query, productsByCode))
                    .toList();
        }
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
        ReusableType type = request.reusableType() == null ? ReusableType.SINGLE_BLOCK : request.reusableType();
        if (type == ReusableType.FRAGMENT) {
            throw new ApiException("Use /reusable-fragments para crear fragmentos");
        }
        String code = request.code() == null || request.code().isBlank()
                ? nextCode(type)
                : request.code().trim();
        if (reusableBlockRepository.existsByCodeIgnoreCase(code)) {
            throw new ApiException("Ya existe contenido reutilizable con codigo " + code);
        }
        ReusableBlock block = ReusableBlock.builder().build();
        applyRequest(request, block, code);
        return reusableBlockMapper.toResponse(reusableBlockRepository.save(block));
    }

    @Override
    @Transactional
    public ReusableBlockResponse createFragment(CreateReusableFragmentRequest request) {
        List<SnapshotBlock> snapshots = bilingualSnapshots(snapshotBlocks(request));
        if (snapshots.isEmpty()) {
            throw new ApiException("No se puede guardar un fragmento sin bloques");
        }
        ReusableBlock fragment = ReusableBlock.builder()
                .code(nextFragmentCode())
                .title(request.name().trim())
                .titleEs(request.name().trim())
                .titleEn(request.name().trim())
                .description(request.description())
                .descriptionEs(request.description())
                .descriptionEn(request.description())
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
        String code = request.code() == null || request.code().isBlank() ? block.getCode() : request.code().trim();
        if (!code.equalsIgnoreCase(block.getCode()) && reusableBlockRepository.existsByCodeIgnoreCase(code)) {
            throw new ApiException("Ya existe contenido reutilizable con codigo " + code);
        }
        applyRequest(request, block, code);
        return reusableBlockMapper.toResponse(reusableBlockRepository.save(block));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        ReusableBlock block = findBlock(id);
        block.setActive(false);
        reusableBlockRepository.save(block);
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

    private void applyRequest(ReusableBlockRequest request, ReusableBlock block, String code) {
        if (request.reusableType() == ReusableType.FRAGMENT) {
            throw new ApiException("Use /reusable-fragments para editar fragmentos");
        }
        String titleEs = firstNotBlank(request.titleEs(), request.title(), block.getTitleEs(), block.getTitle());
        String titleEn = firstNotBlank(request.titleEn(), block.getTitleEn(), titleEs);
        if (titleEs == null) {
            throw new ApiException("El titulo en español es obligatorio");
        }
        if (request.contentJson() == null || request.contentJson().isBlank()) {
            throw new ApiException("El contenido reutilizable no puede estar vacio");
        }
        block.setCode(code);
        block.setTitle(titleEs);
        block.setTitleEs(titleEs);
        block.setTitleEn(titleEn);
        block.setDescription(firstNotBlank(request.descriptionEs(), request.description()));
        block.setDescriptionEs(firstNotBlank(request.descriptionEs(), request.description()));
        block.setDescriptionEn(firstNotBlank(request.descriptionEn(), block.getDescriptionEn(), request.descriptionEs(), request.description()));
        if (request.reusableType() != null) {
            block.setReusableType(request.reusableType());
        } else if (block.getReusableType() == null) {
            block.setReusableType(ReusableType.SINGLE_BLOCK);
        }
        block.setProductCategory(request.productCategory());
        block.setProductCodes(request.productCodes());
        block.setContentJson(normalizeBilingualContent(request.contentJson()));
        block.setActive(request.active());
    }

    private String normalizeBilingualContent(String contentJson) {
        try {
            JsonNode parsed = objectMapper.readTree(contentJson);
            if (!(parsed instanceof ObjectNode root) || !root.path("blocks").isArray()) {
                return contentJson;
            }
            List<ObjectNode> esBlocks = new ArrayList<>();
            List<ObjectNode> enBlocks = new ArrayList<>();
            for (JsonNode node : root.path("blocks")) {
                if (!(node instanceof ObjectNode objectNode)) {
                    continue;
                }
                String language = objectNode.path("languageCode").asText("ES");
                (LanguageCode.EN.name().equals(language) ? enBlocks : esBlocks).add(objectNode.deepCopy());
            }
            esBlocks.sort(Comparator.comparing(node -> node.path("sortOrder").asInt(Integer.MAX_VALUE)));
            enBlocks.sort(Comparator.comparing(node -> node.path("sortOrder").asInt(Integer.MAX_VALUE)));
            int count = Math.max(esBlocks.size(), enBlocks.size());
            ArrayNode normalized = objectMapper.createArrayNode();
            for (int index = 0; index < count; index++) {
                ObjectNode es = alignedBlock(esBlocks, enBlocks, index, LanguageCode.ES);
                ObjectNode en = alignedBlock(enBlocks, esBlocks, index, LanguageCode.EN);
                String canonicalType = es.path("blockType").asText(en.path("blockType").asText());
                es.put("blockType", canonicalType);
                en.put("blockType", canonicalType);
                es.put("sortOrder", index + 1);
                en.put("sortOrder", index + 1);
                normalized.add(es);
                normalized.add(en);
            }
            root.set("blocks", normalized);
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            throw new ApiException("El JSON del contenido reutilizable no es valido", exception);
        }
    }

    private ObjectNode alignedBlock(
            List<ObjectNode> primary,
            List<ObjectNode> fallback,
            int index,
            LanguageCode language
    ) {
        ObjectNode block;
        if (index < primary.size()) {
            block = primary.get(index).deepCopy();
        } else if (index < fallback.size()) {
            block = fallback.get(index).deepCopy();
        } else {
            block = objectMapper.createObjectNode();
        }
        block.put("languageCode", language.name());
        return block;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean matches(ReusableBlock block, String query, Map<String, Product> productsByCode) {
        String text = String.join(" ",
                value(block.getCode()),
                value(block.getTitle()),
                value(block.getTitleEs()),
                value(block.getTitleEn()),
                value(block.getDescription()),
                value(block.getDescriptionEs()),
                value(block.getDescriptionEn()),
                value(block.getProductCategory()),
                value(block.getProductCodes()),
                value(block.getContentJson()),
                productNames(block.getProductCodes(), productsByCode),
                usageText(block.getId(), productsByCode)
        );
        return normalize(text).contains(query);
    }

    private String usageText(Long blockId, Map<String, Product> productsByCode) {
        return findUsages(blockId).stream()
                .map(usage -> String.join(" ",
                        value(usage.manualCode()),
                        value(usage.manualTitle()),
                        value(usage.productCode()),
                        productName(usage.productCode(), productsByCode),
                        value(usage.sectionTitle())
                ))
                .collect(Collectors.joining(" "));
    }

    private String productNames(String productCodes, Map<String, Product> productsByCode) {
        if (productCodes == null) return "";
        return Arrays.stream(productCodes.split("[|,;]"))
                .map(String::trim)
                .map(code -> productName(code, productsByCode))
                .collect(Collectors.joining(" "));
    }

    private String productName(String productCode, Map<String, Product> productsByCode) {
        if (productCode == null) return "";
        Product product = productsByCode.get(productCode.toUpperCase());
        if (product == null) return "";
        return String.join(" ", value(product.getName()), value(product.getNameEs()), value(product.getNameEn()));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .trim();
    }

    private String nextCode(ReusableType type) {
        String prefix = type == ReusableType.FRAGMENT ? "FRG-" : "SEC-";
        int next = reusableBlockRepository.findByCodeStartingWithIgnoreCase(prefix).stream()
                .map(ReusableBlock::getCode)
                .map(this::codeNumber)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        String code;
        do {
            code = prefix + String.format("%03d", next++);
        } while (reusableBlockRepository.existsByCodeIgnoreCase(code));
        return code;
    }

    private int codeNumber(String code) {
        Matcher matcher = CODE_NUMBER.matcher(code == null ? "" : code.trim().toUpperCase());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
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

    private List<SnapshotBlock> bilingualSnapshots(List<SnapshotBlock> snapshots) {
        List<SnapshotBlock> es = snapshots.stream()
                .filter(block -> block.languageCode() == LanguageCode.ES)
                .sorted(Comparator.comparing(block -> block.sortOrder() == null ? Integer.MAX_VALUE : block.sortOrder()))
                .toList();
        List<SnapshotBlock> en = snapshots.stream()
                .filter(block -> block.languageCode() == LanguageCode.EN)
                .sorted(Comparator.comparing(block -> block.sortOrder() == null ? Integer.MAX_VALUE : block.sortOrder()))
                .toList();
        int count = Math.max(es.size(), en.size());
        List<SnapshotBlock> normalized = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            SnapshotBlock esBlock = alignedSnapshot(es, en, index, LanguageCode.ES);
            SnapshotBlock enBlock = alignedSnapshot(en, es, index, LanguageCode.EN);
            BlockType type = esBlock.blockType() != null ? esBlock.blockType() : enBlock.blockType();
            normalized.add(new SnapshotBlock(type, LanguageCode.ES, esBlock.contentJson(), esBlock.plainText(), esBlock.assetId(), index + 1));
            normalized.add(new SnapshotBlock(type, LanguageCode.EN, enBlock.contentJson(), enBlock.plainText(), enBlock.assetId(), index + 1));
        }
        return normalized;
    }

    private SnapshotBlock alignedSnapshot(
            List<SnapshotBlock> primary,
            List<SnapshotBlock> fallback,
            int index,
            LanguageCode language
    ) {
        SnapshotBlock source = index < primary.size()
                ? primary.get(index)
                : fallback.get(index);
        return new SnapshotBlock(
                source.blockType(),
                language,
                source.contentJson(),
                source.plainText(),
                source.assetId(),
                index + 1
        );
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
        return nextCode(ReusableType.FRAGMENT);
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
