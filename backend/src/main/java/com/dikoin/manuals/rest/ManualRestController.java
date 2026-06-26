package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.manual.*;
import com.dikoin.manuals.dtos.notice.BulkNoticeApplyRequest;
import com.dikoin.manuals.dtos.notice.NoticeApplicationResponse;
import com.dikoin.manuals.servicios.ManualService;
import com.dikoin.manuals.servicios.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manuals")
@RequiredArgsConstructor
public class ManualRestController {

    private final ManualService manualService;
    private final NoticeService noticeService;

    @GetMapping
    public List<ManualSummaryResponse> findAll(@RequestParam(required = false) String search) {
        return manualService.findAll(search);
    }

    @GetMapping("/deleted")
    public List<ManualSummaryResponse> findDeleted(@RequestParam(required = false) String search) {
        return manualService.findDeleted(search);
    }

    @GetMapping("/{id}")
    public ManualDetailResponse findById(@PathVariable Long id) {
        return manualService.findById(id);
    }

    @PostMapping
    public ManualDetailResponse create(@Valid @RequestBody ManualCreateRequest request) {
        return manualService.create(request);
    }

    @PutMapping("/{id}")
    public ManualDetailResponse update(@PathVariable Long id, @Valid @RequestBody ManualUpdateRequest request) {
        return manualService.update(id, request);
    }

    @PutMapping("/{manualId}/versions")
    public ManualVersionResponse saveVersion(@PathVariable Long manualId, @Valid @RequestBody ManualVersionRequest request) {
        return manualService.saveVersion(manualId, request);
    }

    @PostMapping("/{manualId}/versions/{versionId}/publish")
    public ManualVersionResponse publishVersion(
            @PathVariable Long manualId,
            @PathVariable Long versionId,
            @RequestBody(required = false) PublishVersionRequest request
    ) {
        return manualService.publishVersion(manualId, versionId, request);
    }

    @PatchMapping("/{id}/enabled")
    public ManualDetailResponse setEnabled(@PathVariable Long id, @RequestBody ManualEnabledRequest request) {
        return manualService.setEnabled(id, request);
    }

    @PostMapping("/bulk/notices")
    public List<NoticeApplicationResponse> applyBulkNotice(@Valid @RequestBody BulkNoticeApplyRequest request) {
        return noticeService.apply(request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        manualService.delete(id);
    }

    @PostMapping("/{id}/restore")
    public ManualDetailResponse restore(@PathVariable Long id, @Valid @RequestBody(required = false) ManualRestoreRequest request) {
        return manualService.restore(id, request);
    }

    @DeleteMapping("/{id}/permanent")
    public void deletePermanently(@PathVariable Long id) {
        manualService.deletePermanently(id);
    }
}
