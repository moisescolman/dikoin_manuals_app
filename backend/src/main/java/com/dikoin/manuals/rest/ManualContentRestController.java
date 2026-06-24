package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.manual.*;
import com.dikoin.manuals.servicios.ManualContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manual-content")
@RequiredArgsConstructor
public class ManualContentRestController {

    private final ManualContentService manualContentService;

    @GetMapping("/versions/{versionId}/sections")
    public List<ManualSectionResponse> findSections(@PathVariable Long versionId) {
        return manualContentService.findSections(versionId);
    }

    @PostMapping("/versions/{versionId}/sections")
    public ManualSectionResponse createRootSection(
            @PathVariable Long versionId,
            @Valid @RequestBody ManualSectionRequest request
    ) {
        return manualContentService.createSection(versionId, null, request);
    }

    @PostMapping("/sections/{parentSectionId}/subsections")
    public ManualSectionResponse createSubsection(
            @PathVariable Long parentSectionId,
            @Valid @RequestBody ManualSectionRequest request
    ) {
        return manualContentService.createSection(null, parentSectionId, request);
    }

    @PatchMapping("/sections/{sectionId}")
    public ManualSectionResponse updateSection(
            @PathVariable Long sectionId,
            @RequestBody ManualSectionPatchRequest request
    ) {
        return manualContentService.updateSection(sectionId, request);
    }

    @PutMapping("/versions/{versionId}/sections/reorder")
    public List<ManualSectionResponse> reorderSections(
            @PathVariable Long versionId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return manualContentService.reorderSections(versionId, request);
    }

    @DeleteMapping("/sections/{sectionId}")
    public void deleteSection(
            @PathVariable Long sectionId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        manualContentService.deleteSection(sectionId, force);
    }

    @PostMapping("/versions/{versionId}/sections/renumber")
    public List<ManualSectionResponse> recalculateNumbers(@PathVariable Long versionId) {
        return manualContentService.recalculateNumbers(versionId);
    }

    @GetMapping("/sections/{sectionId}/blocks")
    public List<ManualBlockResponse> findBlocks(@PathVariable Long sectionId) {
        return manualContentService.findBlocks(sectionId);
    }

    @PostMapping("/sections/{sectionId}/blocks")
    public ManualBlockResponse createBlock(
            @PathVariable Long sectionId,
            @Valid @RequestBody ManualBlockRequest request
    ) {
        return manualContentService.createBlock(sectionId, request);
    }

    @PutMapping("/blocks/{blockId}")
    public ManualBlockResponse updateBlock(
            @PathVariable Long blockId,
            @Valid @RequestBody ManualBlockRequest request
    ) {
        return manualContentService.updateBlock(blockId, request);
    }

    @DeleteMapping("/blocks/{blockId}")
    public void deleteBlock(@PathVariable Long blockId) {
        manualContentService.deleteBlock(blockId);
    }

    @PostMapping("/blocks/{blockId}/duplicate")
    public ManualBlockResponse duplicateBlock(@PathVariable Long blockId) {
        return manualContentService.duplicateBlock(blockId);
    }

    @PostMapping("/blocks/{blockId}/move")
    public ManualBlockResponse moveBlock(
            @PathVariable Long blockId,
            @Valid @RequestBody MoveBlockRequest request
    ) {
        return manualContentService.moveBlock(blockId, request);
    }

    @PutMapping("/sections/{sectionId}/blocks/reorder")
    public List<ManualBlockResponse> reorderBlocks(
            @PathVariable Long sectionId,
            @Valid @RequestBody ReorderRequest request
    ) {
        return manualContentService.reorderBlocks(sectionId, request);
    }
}
