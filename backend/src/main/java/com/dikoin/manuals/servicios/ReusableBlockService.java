package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.reusableblock.*;

import java.util.List;

public interface ReusableBlockService {
    List<ReusableBlockResponse> findAll(boolean includeInactive);
    ReusableBlockResponse findById(Long id);
    ReusableBlockResponse create(ReusableBlockRequest request);
    ReusableBlockResponse createFragment(CreateReusableFragmentRequest request);
    ReusableFragmentInsertResponse insertFragment(Long id, InsertReusableFragmentRequest request);
    ReusableBlockResponse update(Long id, ReusableBlockRequest request);
    List<ReusableBlockUsageResponse> findUsages(Long id);
}
