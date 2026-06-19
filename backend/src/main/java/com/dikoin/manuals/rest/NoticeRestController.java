package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.notice.BulkNoticeApplyRequest;
import com.dikoin.manuals.dtos.notice.NoticeApplicationResponse;
import com.dikoin.manuals.dtos.notice.NoticeTemplateRequest;
import com.dikoin.manuals.dtos.notice.NoticeTemplateResponse;
import com.dikoin.manuals.dtos.notice.NoticeUsageResponse;
import com.dikoin.manuals.enums.NoticeType;
import com.dikoin.manuals.servicios.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeRestController {

    private final NoticeService noticeService;

    @GetMapping
    public List<NoticeTemplateResponse> findAll(@RequestParam(required = false) NoticeType type) {
        return noticeService.findAll(type);
    }

    @PostMapping
    public NoticeTemplateResponse create(@Valid @RequestBody NoticeTemplateRequest request) {
        return noticeService.create(request);
    }

    @PutMapping("/{id}")
    public NoticeTemplateResponse update(@PathVariable Long id, @Valid @RequestBody NoticeTemplateRequest request) {
        return noticeService.update(id, request);
    }

    @GetMapping("/{id}/usages")
    public List<NoticeUsageResponse> findUsages(@PathVariable Long id) {
        return noticeService.findUsages(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        noticeService.delete(id);
    }

    @PostMapping("/apply")
    public List<NoticeApplicationResponse> apply(@Valid @RequestBody BulkNoticeApplyRequest request) {
        return noticeService.apply(request);
    }
}
