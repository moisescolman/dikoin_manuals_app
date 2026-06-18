package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.reusableblock.ReusableBlockRequest;
import com.dikoin.manuals.dtos.reusableblock.ReusableBlockResponse;
import com.dikoin.manuals.dtos.reusableblock.ReusableBlockUsageResponse;

import java.util.List;

public interface ReusableBlockService {
    List<ReusableBlockResponse> findAll(boolean includeInactive);
    ReusableBlockResponse findById(Long id);
    ReusableBlockResponse create(ReusableBlockRequest request);
    ReusableBlockResponse update(Long id, ReusableBlockRequest request);
    List<ReusableBlockUsageResponse> findUsages(Long id);
}
