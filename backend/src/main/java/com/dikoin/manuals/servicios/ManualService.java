package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.manual.*;

import java.util.List;

public interface ManualService {
    List<ManualSummaryResponse> findAll(String search);
    List<ManualSummaryResponse> findDeleted(String search);
    ManualDetailResponse findById(Long id);
    ManualDetailResponse create(ManualCreateRequest request);
    ManualDetailResponse update(Long id, ManualUpdateRequest request);
    ManualVersionResponse saveVersion(Long manualId, ManualVersionRequest request);
    ManualVersionResponse publishVersion(Long manualId, Long versionId, PublishVersionRequest request);
    ManualDetailResponse setEnabled(Long id, ManualEnabledRequest request);
    void delete(Long id);
    ManualDetailResponse restore(Long id, ManualRestoreRequest request);
    void deletePermanently(Long id);
}
