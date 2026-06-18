package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.notice.BulkNoticeApplyRequest;
import com.dikoin.manuals.dtos.notice.NoticeApplicationResponse;
import com.dikoin.manuals.dtos.notice.NoticeTemplateRequest;
import com.dikoin.manuals.dtos.notice.NoticeTemplateResponse;
import com.dikoin.manuals.entidades.*;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private final NoticeTemplateRepository noticeTemplateRepository;
    private final NoticeApplicationRepository noticeApplicationRepository;
    private final ManualRepository manualRepository;
    private final ProductRepository productRepository;
    private final ManualSectionRepository manualSectionRepository;
    private final NoticeMapper noticeMapper;

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
        if (noticeTemplateRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ApiException("Ya existe un aviso con codigo " + request.code());
        }
        NoticeTemplate notice = NoticeTemplate.builder().build();
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
        return noticeTemplateRepository.save(NoticeTemplate.builder()
                .code(request.code())
                .type(request.type())
                .titleEs(request.titleEs())
                .titleEn(request.titleEn())
                .productCategory(request.productCategory())
                .productCodes(request.productCodes())
                .contentEs(request.contentEs())
                .contentEn(request.contentEn())
                .active(true)
                .build());
    }

    private void applyRequest(NoticeTemplateRequest request, NoticeTemplate notice) {
        notice.setCode(request.code());
        notice.setType(request.type());
        notice.setTitleEs(request.titleEs());
        notice.setTitleEn(request.titleEn());
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
