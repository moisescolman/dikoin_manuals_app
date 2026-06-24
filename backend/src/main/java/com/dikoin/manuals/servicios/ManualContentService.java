package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.manual.*;

import java.util.List;

public interface ManualContentService {
    List<ManualSectionResponse> findSections(Long versionId);
    ManualSectionResponse createSection(Long versionId, Long parentSectionId, ManualSectionRequest request);
    ManualSectionResponse updateSection(Long sectionId, ManualSectionPatchRequest request);
    List<ManualSectionResponse> reorderSections(Long versionId, ReorderRequest request);
    void deleteSection(Long sectionId, boolean force);
    List<ManualSectionResponse> recalculateNumbers(Long versionId);
    List<ManualBlockResponse> findBlocks(Long sectionId);
    ManualBlockResponse createBlock(Long sectionId, ManualBlockRequest request);
    ManualBlockResponse updateBlock(Long blockId, ManualBlockRequest request);
    void deleteBlock(Long blockId);
    ManualBlockResponse duplicateBlock(Long blockId);
    ManualBlockResponse moveBlock(Long blockId, MoveBlockRequest request);
    List<ManualBlockResponse> reorderBlocks(Long sectionId, ReorderRequest request);
}
