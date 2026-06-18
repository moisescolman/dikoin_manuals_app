package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.DashboardResponse;
import com.dikoin.manuals.enums.ManualStatus;
import com.dikoin.manuals.repositorios.ManualRepository;
import com.dikoin.manuals.repositorios.ManualVersionRepository;
import com.dikoin.manuals.repositorios.ProductRepository;
import com.dikoin.manuals.servicios.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ProductRepository productRepository;
    private final ManualRepository manualRepository;
    private final ManualVersionRepository manualVersionRepository;

    @Override
    public DashboardResponse getDashboard() {
        return new DashboardResponse(
                productRepository.count(),
                manualRepository.count(),
                manualVersionRepository.countByStatus(ManualStatus.PUBLISHED),
                manualVersionRepository.countByStatus(ManualStatus.DRAFT),
                manualVersionRepository.countByStatus(ManualStatus.REVIEW),
                manualVersionRepository.countByEnReadyFalse()
        );
    }
}
