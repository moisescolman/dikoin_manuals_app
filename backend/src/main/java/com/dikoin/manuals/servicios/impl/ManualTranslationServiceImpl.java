package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.manual.ManualTranslationMode;
import com.dikoin.manuals.dtos.manual.ManualTranslationRequest;
import com.dikoin.manuals.dtos.manual.ManualTranslationResponse;
import com.dikoin.manuals.entidades.ManualBlock;
import com.dikoin.manuals.entidades.ManualSection;
import com.dikoin.manuals.entidades.ManualVersion;
import com.dikoin.manuals.enums.BlockType;
import com.dikoin.manuals.enums.LanguageCode;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.repositorios.ManualVersionRepository;
import com.dikoin.manuals.servicios.GeminiTranslationService;
import com.dikoin.manuals.servicios.ManualTranslationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ManualTranslationServiceImpl implements ManualTranslationService {

    private static final int MAX_BATCH_ITEMS = 24;
    private static final int MAX_BATCH_CHARS = 12000;

    private final ManualVersionRepository manualVersionRepository;
    private final GeminiTranslationService geminiTranslationService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ManualTranslationResponse translateToEnglish(Long manualVersionId, ManualTranslationRequest request) {
        ManualVersion version = manualVersionRepository.findById(manualVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version no encontrada: " + manualVersionId));
        ManualTranslationMode mode = request != null && request.mode() != null ? request.mode() : ManualTranslationMode.FULL_MANUAL;
        boolean overwriteExisting = Boolean.TRUE.equals(request != null ? request.overwriteExisting() : null);

        List<ManualSection> selectedSections = selectedSections(version, mode, request == null ? List.of() : request.sectionIds());
        if (selectedSections.isEmpty()) {
            throw new ApiException(mode == ManualTranslationMode.SELECTED_SECTIONS
                    ? "Selecciona al menos una seccion."
                    : "No hay secciones disponibles para traducir.");
        }
        boolean hasSpanish = selectedSections.stream().anyMatch(this::hasSpanishContent);
        if (!hasSpanish) {
            throw new ApiException("No hay contenido en espanol para traducir.");
        }

        List<String> errors = new ArrayList<>();
        int translatedSections = 0;
        int translatedBlocks = 0;
        int skippedSections = 0;

        for (ManualSection section : selectedSections) {
            if (!hasSpanishContent(section)) {
                skippedSections++;
                continue;
            }
            if (!overwriteExisting && hasEnglishContent(section)) {
                skippedSections++;
                continue;
            }

            SectionTranslationResult result = translateSection(section, overwriteExisting, errors);
            if (result.changed()) {
                translatedSections++;
                translatedBlocks += result.blocks();
            }
        }

        version.setEnReady(version.getSections().stream().anyMatch(this::hasEnglishContent));
        manualVersionRepository.save(version);

        String status = errors.isEmpty() ? "COMPLETED" : translatedSections > 0 ? "PARTIAL" : "FAILED";
        return new ManualTranslationResponse(
                status,
                version.getId(),
                LanguageCode.EN,
                translatedSections,
                translatedBlocks,
                skippedSections,
                errors
        );
    }

    private List<ManualSection> selectedSections(ManualVersion version, ManualTranslationMode mode, List<Long> sectionIds) {
        List<ManualSection> sections = version.getSections().stream()
                .sorted(Comparator.comparing(ManualSection::getSortOrder))
                .toList();
        if (mode == ManualTranslationMode.FULL_MANUAL) {
            return sections;
        }
        if (sectionIds == null || sectionIds.isEmpty()) {
            return List.of();
        }
        Set<Long> selected = new HashSet<>(sectionIds);
        return sections.stream()
                .filter(section -> section.getId() != null && selected.contains(section.getId()))
                .toList();
    }

    private SectionTranslationResult translateSection(ManualSection section, boolean overwriteExisting, List<String> errors) {
        List<BlockWork> blocks = section.getBlocks().stream()
                .filter(block -> block.getLanguageCode() == LanguageCode.ES)
                .sorted(Comparator.comparing(ManualBlock::getSortOrder))
                .map(this::prepareBlockWork)
                .toList();

        TranslationField titleField = null;
        String title = section.getTitleEs();
        if (title != null && !title.isBlank()) {
            titleField = new TranslationField(
                    "section_%s_title".formatted(section.getId()),
                    BlockType.HEADING,
                    title,
                    section::setTitleEn
            );
        }

        List<TranslationField> fields = new ArrayList<>();
        if (titleField != null) fields.add(titleField);
        blocks.forEach(block -> fields.addAll(block.fields()));

        Map<String, String> translations = translateFields(fields, errors, section);

        if (titleField != null && translations.containsKey(titleField.id())) {
            titleField.apply(translations.get(titleField.id()));
        } else if (overwriteExisting) {
            section.setTitleEn(null);
        }

        if (overwriteExisting) {
            section.getBlocks().removeIf(block -> block.getLanguageCode() == LanguageCode.EN);
        }

        int savedBlocks = 0;
        for (BlockWork block : blocks) {
            boolean complete = block.fields().stream().allMatch(field -> translations.containsKey(field.id()));
            if (!block.copyOnly() && !complete) {
                errors.add("No se pudo traducir el bloque " + block.source().getId() + " de la seccion " + sectionLabel(section) + ".");
                continue;
            }
            block.fields().forEach(field -> field.apply(translations.get(field.id())));
            section.getBlocks().add(copyEnglishBlock(section, block.source(), block.contentJson()));
            savedBlocks++;
        }

        return new SectionTranslationResult(savedBlocks > 0 || (titleField != null && translations.containsKey(titleField.id())), savedBlocks);
    }

    private BlockWork prepareBlockWork(ManualBlock source) {
        if (!shouldTranslate(source.getBlockType())) {
            return new BlockWork(source, null, List.of(), true);
        }

        ObjectNode root = parseContent(source);
        List<TranslationField> fields = new ArrayList<>();
        collectFieldsForBlock(source, root, fields);

        if (fields.isEmpty()) {
            return new BlockWork(source, root, List.of(), true);
        }
        return new BlockWork(source, root, fields, false);
    }

    private Map<String, String> translateFields(List<TranslationField> fields, List<String> errors, ManualSection section) {
        Map<String, String> translations = new HashMap<>();
        List<GeminiTranslationService.TranslationTextItem> batch = new ArrayList<>();
        int chars = 0;

        for (TranslationField field : fields) {
            int nextChars = chars + field.text().length();
            if (!batch.isEmpty() && (batch.size() >= MAX_BATCH_ITEMS || nextChars > MAX_BATCH_CHARS)) {
                translateBatch(batch, translations, errors, section);
                batch.clear();
                chars = 0;
            }
            batch.add(new GeminiTranslationService.TranslationTextItem(field.id(), field.blockType(), field.text()));
            chars += field.text().length();
        }
        translateBatch(batch, translations, errors, section);
        return translations;
    }

    private void translateBatch(
            List<GeminiTranslationService.TranslationTextItem> batch,
            Map<String, String> translations,
            List<String> errors,
            ManualSection section
    ) {
        if (batch.isEmpty()) return;
        try {
            translations.putAll(geminiTranslationService.translateToEnglish(batch));
        } catch (ApiException exception) {
            String message = exception.getMessage() == null ? "" : exception.getMessage();
            if (message.contains("GEMINI_API_KEY") || message.contains("Gemini esta deshabilitado")) {
                throw exception;
            }
            errors.add("Lote no traducido en la seccion " + sectionLabel(section) + ": " + message);
        }
    }

    private ManualBlock copyEnglishBlock(ManualSection section, ManualBlock source, ObjectNode translatedJson) {
        String contentJson = translatedJson == null ? source.getContentJson() : writeJson(translatedJson);
        return ManualBlock.builder()
                .section(section)
                .sortOrder(source.getSortOrder())
                .blockType(source.getBlockType())
                .languageCode(LanguageCode.EN)
                .contentJson(contentJson)
                .plainText(plainText(contentJson, source.getPlainText()))
                .asset(source.getAsset())
                .reusableBlock(source.getReusableBlock())
                .reusableFragment(source.getReusableFragment())
                .build();
    }

    private void collectFieldsForBlock(ManualBlock source, ObjectNode root, List<TranslationField> fields) {
        String prefix = "block_%s".formatted(source.getId());
        if (source.getBlockType() == BlockType.TABLE) {
            collectStringArray(root.path("columns"), "%s_columns".formatted(prefix), source.getBlockType(), fields);
            collectTableRows(root.path("rows"), "%s_rows".formatted(prefix), source.getBlockType(), fields);
            JsonNode json = root.path("json");
            if (json.isObject()) {
                collectTextNodes((ObjectNode) json, "%s_json".formatted(prefix), source.getBlockType(), fields);
            }
            return;
        }

        collectField(root, "text", "%s_text".formatted(prefix), source.getBlockType(), fields);
        collectField(root, "title", "%s_title".formatted(prefix), source.getBlockType(), fields);
        collectStringArray(root.path("items"), "%s_items".formatted(prefix), source.getBlockType(), fields);
        JsonNode json = root.path("json");
        if (json.isObject()) {
            collectTextNodes((ObjectNode) json, "%s_json".formatted(prefix), source.getBlockType(), fields);
        }
    }

    private void collectField(ObjectNode root, String fieldName, String id, BlockType type, List<TranslationField> fields) {
        JsonNode node = root.path(fieldName);
        if (!node.isTextual() || !isTranslatable(node.asText())) return;
        fields.add(new TranslationField(id, type, node.asText(), value -> root.put(fieldName, value)));
    }

    private void collectStringArray(JsonNode node, String prefix, BlockType type, List<TranslationField> fields) {
        if (!node.isArray()) return;
        ArrayNode array = (ArrayNode) node;
        for (int index = 0; index < array.size(); index++) {
            JsonNode value = array.get(index);
            if (!value.isTextual() || !isTranslatable(value.asText())) continue;
            int currentIndex = index;
            fields.add(new TranslationField("%s_%d".formatted(prefix, index), type, value.asText(), text -> array.set(currentIndex, textNode(text))));
        }
    }

    private void collectTableRows(JsonNode node, String prefix, BlockType type, List<TranslationField> fields) {
        if (!node.isArray()) return;
        ArrayNode rows = (ArrayNode) node;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            JsonNode row = rows.get(rowIndex);
            if (!row.isArray()) continue;
            ArrayNode cells = (ArrayNode) row;
            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                JsonNode cell = cells.get(cellIndex);
                if (!cell.isTextual() || !isTranslatable(cell.asText())) continue;
                int currentRow = rowIndex;
                int currentCell = cellIndex;
                fields.add(new TranslationField(
                        "%s_%d_%d".formatted(prefix, rowIndex, cellIndex),
                        type,
                        cell.asText(),
                        text -> ((ArrayNode) rows.get(currentRow)).set(currentCell, textNode(text))
                ));
            }
        }
    }

    private void collectTextNodes(ObjectNode node, String prefix, BlockType type, List<TranslationField> fields) {
        List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
        node.fields().forEachRemaining(entries::add);
        for (Map.Entry<String, JsonNode> entry : entries) {
            String name = entry.getKey();
            JsonNode child = entry.getValue();
            String id = "%s_%s".formatted(prefix, sanitizeId(name));
            if ("text".equals(name) && child.isTextual() && isTranslatable(child.asText())) {
                fields.add(new TranslationField(id, type, child.asText(), value -> node.put(name, value)));
            } else if (child.isObject()) {
                collectTextNodes((ObjectNode) child, id, type, fields);
            } else if (child.isArray()) {
                collectTextArray((ArrayNode) child, id, type, fields);
            }
        }
    }

    private void collectTextArray(ArrayNode array, String prefix, BlockType type, List<TranslationField> fields) {
        for (int index = 0; index < array.size(); index++) {
            JsonNode child = array.get(index);
            String id = "%s_%d".formatted(prefix, index);
            if (child.isObject()) {
                collectTextNodes((ObjectNode) child, id, type, fields);
            } else if (child.isArray()) {
                collectTextArray((ArrayNode) child, id, type, fields);
            }
        }
    }

    private ObjectNode parseContent(ManualBlock source) {
        try {
            JsonNode parsed = objectMapper.readTree(source.getContentJson());
            if (parsed.isObject()) {
                return ((ObjectNode) parsed).deepCopy();
            }
        } catch (Exception ignored) {
            // Fallback below creates a simple text payload for legacy plain text blocks.
        }
        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.put("type", source.getBlockType().name().toLowerCase());
        fallback.put("text", source.getPlainText() == null ? source.getContentJson() : source.getPlainText());
        return fallback;
    }

    private boolean shouldTranslate(BlockType blockType) {
        return blockType == BlockType.HEADING
                || blockType == BlockType.PARAGRAPH
                || blockType == BlockType.NOTE
                || blockType == BlockType.WARNING
                || blockType == BlockType.INFO_BOX
                || blockType == BlockType.ORDERED_LIST
                || blockType == BlockType.UNORDERED_LIST
                || blockType == BlockType.TABLE;
    }

    private boolean hasSpanishContent(ManualSection section) {
        return (section.getTitleEs() != null && !section.getTitleEs().isBlank())
                || section.getBlocks().stream().anyMatch(block -> block.getLanguageCode() == LanguageCode.ES);
    }

    private boolean hasEnglishContent(ManualSection section) {
        return (section.getTitleEn() != null && !section.getTitleEn().isBlank())
                || section.getBlocks().stream().anyMatch(block -> block.getLanguageCode() == LanguageCode.EN);
    }

    private boolean isTranslatable(String value) {
        if (value == null || value.isBlank()) return false;
        return value.chars().anyMatch(Character::isLetter);
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new ApiException("No se pudo reconstruir el JSON traducido.", exception);
        }
    }

    private String plainText(String contentJson, String fallback) {
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            if (root.path("text").isTextual()) return root.path("text").asText();
            if (root.path("title").isTextual()) return root.path("title").asText();
            if (root.path("items").isArray()) {
                List<String> items = new ArrayList<>();
                root.path("items").forEach(item -> {
                    if (item.isTextual()) items.add(item.asText());
                });
                return String.join("\n", items);
            }
        } catch (Exception ignored) {
            return fallback;
        }
        return fallback;
    }

    private com.fasterxml.jackson.databind.node.TextNode textNode(String value) {
        return objectMapper.getNodeFactory().textNode(value);
    }

    private String sanitizeId(String value) {
        return value.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private String sectionLabel(ManualSection section) {
        return (section.getSectionNumber() == null ? String.valueOf(section.getSortOrder()) : section.getSectionNumber())
                + " " + section.getTitleEs();
    }

    private record TranslationField(
            String id,
            BlockType blockType,
            String text,
            Consumer<String> writer
    ) {
        void apply(String value) {
            if (value != null) {
                writer.accept(value);
            }
        }
    }

    private record BlockWork(
            ManualBlock source,
            ObjectNode translatedJson,
            List<TranslationField> fields,
            boolean copyOnly
    ) {
        ObjectNode contentJson() {
            return translatedJson;
        }
    }

    private record SectionTranslationResult(boolean changed, int blocks) {
    }
}
