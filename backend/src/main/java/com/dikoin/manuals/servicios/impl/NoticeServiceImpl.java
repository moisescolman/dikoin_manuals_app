package com.dikoin.manuals.servicios.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dikoin.manuals.dtos.notice.BulkNoticeApplyRequest;
import com.dikoin.manuals.dtos.notice.NoticeApplicationResponse;
import com.dikoin.manuals.dtos.notice.NoticeTemplateRequest;
import com.dikoin.manuals.dtos.notice.NoticeTemplateResponse;
import com.dikoin.manuals.dtos.notice.NoticeUsageResponse;
import com.dikoin.manuals.entidades.*;
import com.dikoin.manuals.enums.BlockType;
import com.dikoin.manuals.enums.NoticeApplyScope;
import com.dikoin.manuals.enums.NoticeType;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.NoticeMapper;
import com.dikoin.manuals.repositorios.*;
import com.dikoin.manuals.servicios.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private static final String NOTE_CODE_PREFIX = "NOT-";
    private static final Pattern NOTE_CODE_PATTERN = Pattern.compile("^NOT-(\\d+)$", Pattern.CASE_INSENSITIVE);

    private final NoticeTemplateRepository noticeTemplateRepository;
    private final NoticeApplicationRepository noticeApplicationRepository;
    private final ManualRepository manualRepository;
    private final ProductRepository productRepository;
    private final ManualSectionRepository manualSectionRepository;
    private final ManualBlockRepository manualBlockRepository;
    private final NoticeMapper noticeMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<NoticeTemplateResponse> findAll(NoticeType type) {
        List<NoticeTemplate> notices = type == null
                ? noticeTemplateRepository.findByActiveTrueOrderByUpdatedAtDesc()
                : noticeTemplateRepository.findByTypeAndActiveTrueOrderByUpdatedAtDesc(type);
        return notices.stream().map(noticeMapper::toTemplateResponse).toList();
    }

    @Override
    @Transactional
    public NoticeTemplateResponse create(NoticeTemplateRequest request) {
        NoticeTemplate notice = NoticeTemplate.builder().build();
        notice.setCode(resolveNewNoticeCode(request.code()));
        applyRequest(request, notice);
        return noticeMapper.toTemplateResponse(noticeTemplateRepository.save(notice));
    }

    @Override
    @Transactional
    public NoticeTemplateResponse update(Long id, NoticeTemplateRequest request) {
        NoticeTemplate notice = findNotice(id);
        applyRequest(request, notice);
        return noticeMapper.toTemplateResponse(noticeTemplateRepository.save(notice));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoticeUsageResponse> findUsages(Long id) {
        findNotice(id);
        String token = "\"noticeTemplateId\"";
        return manualBlockRepository.findByContentJsonContaining(token).stream()
                .filter(block -> referencesNotice(block, id))
                .filter(block -> block.getSection().getManualVersion().getManual().getDeletedAt() == null)
                .map(noticeMapper::toUsageResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        NoticeTemplate notice = findNotice(id);
        String token = "\"noticeTemplateId\"";
        List<ManualBlock> linkedBlocks = manualBlockRepository.findByContentJsonContaining(token).stream()
                .filter(block -> referencesNotice(block, id))
                .toList();
        linkedBlocks.forEach(block -> {
            block.setBlockType(BlockType.NOTE);
            block.setContentJson(noteSnapshotJson(notice));
        });
        manualBlockRepository.saveAll(linkedBlocks);
        notice.setActive(false);
        noticeTemplateRepository.save(notice);
    }

    @Override
    @Transactional
    public List<NoticeApplicationResponse> apply(BulkNoticeApplyRequest request) {
        NoticeTemplate notice = request.noticeTemplateId() != null
                ? findNotice(request.noticeTemplateId())
                : createInlineNotice(request.notice());

        List<NoticeApplication> applications = new ArrayList<>();
        NoticeApplyScope scope = request.applyScope();

        if (scope == NoticeApplyScope.MANUAL) {
            requireNotEmpty(request.manualIds(), "manualIds");
            request.manualIds().forEach(id -> applications.add(NoticeApplication.builder()
                    .noticeTemplate(notice)
                    .manual(findManual(id))
                    .applyScope(scope)
                    .build()));
        } else if (scope == NoticeApplyScope.PRODUCT) {
            requireNotEmpty(request.productIds(), "productIds");
            request.productIds().forEach(id -> applications.add(NoticeApplication.builder()
                    .noticeTemplate(notice)
                    .product(findProduct(id))
                    .applyScope(scope)
                    .build()));
        } else if (scope == NoticeApplyScope.SECTION) {
            requireNotEmpty(request.sectionIds(), "sectionIds");
            request.sectionIds().forEach(id -> applications.add(NoticeApplication.builder()
                    .noticeTemplate(notice)
                    .section(findSection(id))
                    .applyScope(scope)
                    .build()));
        }

        return noticeApplicationRepository.saveAll(applications)
                .stream()
                .map(noticeMapper::toApplicationResponse)
                .toList();
    }

    private NoticeTemplate createInlineNotice(NoticeTemplateRequest request) {
        if (request == null) {
            throw new ApiException("Debe indicar noticeTemplateId o notice");
        }
        String code = resolveNewNoticeCode(request.code());
        return noticeTemplateRepository.save(NoticeTemplate.builder()
                .code(code)
                .type(request.type())
                .titleEs(request.titleEs())
                .titleEn(request.titleEn())
                .visibleTitleEs(resolveVisibleTitle(request.visibleTitleEs()))
                .visibleTitleEn(request.visibleTitleEn())
                .productCategory(request.productCategory())
                .productCodes(request.productCodes())
                .contentEs(request.contentEs())
                .contentEn(request.contentEn())
                .active(true)
                .build());
    }

    private void applyRequest(NoticeTemplateRequest request, NoticeTemplate notice) {
        notice.setType(request.type());
        notice.setTitleEs(request.titleEs());
        notice.setTitleEn(request.titleEn());
        notice.setVisibleTitleEs(resolveVisibleTitle(request.visibleTitleEs()));
        notice.setVisibleTitleEn(request.visibleTitleEn());
        notice.setProductCategory(request.productCategory());
        notice.setProductCodes(request.productCodes());
        notice.setContentEs(request.contentEs());
        notice.setContentEn(request.contentEn());
        notice.setActive(request.active());
    }

    private NoticeTemplate findNotice(Long id) {
        return noticeTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Aviso no encontrado: " + id));
    }

    private String noteSnapshotJson(NoticeTemplate notice) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "note");
        payload.put("text", notice.getContentEs());
        payload.put("title", resolveVisibleTitle(notice.getVisibleTitleEs()));
        payload.put("sourceNoticeId", notice.getId());
        payload.put("sourceNoticeCode", notice.getCode());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException("No se pudo conservar la nota en los manuales", exception);
        }
    }

    private boolean referencesNotice(ManualBlock block, Long noticeId) {
        try {
            JsonNode content = objectMapper.readTree(block.getContentJson());
            return content.has("noticeTemplateId") && content.get("noticeTemplateId").asLong() == noticeId;
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private String resolveNewNoticeCode(String requestedCode) {
        if (!isBlank(requestedCode)) {
            if (noticeTemplateRepository.existsByCodeIgnoreCase(requestedCode.trim())) {
                throw new ApiException("Ya existe un aviso con codigo " + requestedCode.trim());
            }
            return requestedCode.trim();
        }
        int nextNumber = noticeTemplateRepository.findByCodeStartingWithIgnoreCase(NOTE_CODE_PREFIX).stream()
                .map(NoticeTemplate::getCode)
                .map(this::noticeCodeNumber)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
        String code;
        do {
            code = NOTE_CODE_PREFIX + String.format("%03d", nextNumber++);
        } while (noticeTemplateRepository.existsByCodeIgnoreCase(code));
        return code;
    }

    private int noticeCodeNumber(String code) {
        if (code == null) {
            return 0;
        }
        Matcher matcher = NOTE_CODE_PATTERN.matcher(code.trim());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private String resolveVisibleTitle(String value) {
        return isBlank(value) ? "Nota" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Manual findManual(Long id) {
        return manualRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manual no encontrado: " + id));
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
    }

    private ManualSection findSection(Long id) {
        return manualSectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Seccion no encontrada: " + id));
    }

    private void requireNotEmpty(List<Long> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new ApiException("Debe indicar " + fieldName);
        }
    }
}
