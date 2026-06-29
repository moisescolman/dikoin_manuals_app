package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.reusableblock.CreateReusableFragmentRequest;
import com.dikoin.manuals.dtos.reusableblock.InsertReusableFragmentRequest;
import com.dikoin.manuals.dtos.reusablefragment.*;

import java.util.List;

public interface ReusableFragmentService {
    List<ReusableFragmentResponse> findAll(boolean includeInactive, String search);
    ReusableFragmentResponse findById(Long id);
    ReusableFragmentResponse create(ReusableFragmentRequest request);
    ReusableFragmentResponse createFromSelection(CreateReusableFragmentRequest request);
    ReusableFragmentInsertResponse insertFragment(Long id, InsertReusableFragmentRequest request);
    ReusableFragmentResponse update(Long id, ReusableFragmentRequest request);
    void delete(Long id);
    List<ReusableFragmentUsageResponse> findUsages(Long id);
}
