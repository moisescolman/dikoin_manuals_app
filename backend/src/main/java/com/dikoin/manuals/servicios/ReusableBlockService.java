package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.reusableblock.*;
import com.dikoin.manuals.enums.ReusableType;

import java.util.List;

public interface ReusableBlockService {
    List<ReusableBlockResponse> findAll(boolean includeInactive, ReusableType type, String search);
    ReusableBlockResponse findById(Long id);
    ReusableBlockResponse create(ReusableBlockRequest request);
    ReusableBlockResponse createFragment(CreateReusableFragmentRequest request);
    ReusableFragmentInsertResponse insertFragment(Long id, InsertReusableFragmentRequest request);
    ReusableBlockResponse update(Long id, ReusableBlockRequest request);
    void delete(Long id);
    List<ReusableBlockUsageResponse> findUsages(Long id);
}
