package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.reusableblock.ReusableBlockRequest;
import com.dikoin.manuals.dtos.reusableblock.ReusableBlockResponse;
import com.dikoin.manuals.dtos.reusableblock.ReusableBlockUsageResponse;
import com.dikoin.manuals.entidades.ReusableBlock;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.ReusableBlockMapper;
import com.dikoin.manuals.repositorios.ManualBlockRepository;
import com.dikoin.manuals.repositorios.ReusableBlockRepository;
import com.dikoin.manuals.servicios.ReusableBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReusableBlockServiceImpl implements ReusableBlockService {

    private final ReusableBlockRepository reusableBlockRepository;
    private final ManualBlockRepository manualBlockRepository;
    private final ReusableBlockMapper reusableBlockMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ReusableBlockResponse> findAll(boolean includeInactive) {
        List<ReusableBlock> blocks = includeInactive
                ? reusableBlockRepository.findAllByOrderByUpdatedAtDesc()
                : reusableBlockRepository.findByActiveTrueOrderByUpdatedAtDesc();
        return blocks.stream().map(reusableBlockMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReusableBlockResponse findById(Long id) {
        return reusableBlockMapper.toResponse(findBlock(id));
    }

    @Override
    @Transactional
    public ReusableBlockResponse create(ReusableBlockRequest request) {
        if (reusableBlockRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ApiException("Ya existe un bloque con codigo " + request.code());
        }
        ReusableBlock block = ReusableBlock.builder().build();
        applyRequest(request, block);
        return reusableBlockMapper.toResponse(reusableBlockRepository.save(block));
    }

    @Override
    @Transactional
    public ReusableBlockResponse update(Long id, ReusableBlockRequest request) {
        ReusableBlock block = findBlock(id);
        applyRequest(request, block);
        return reusableBlockMapper.toResponse(reusableBlockRepository.save(block));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReusableBlockUsageResponse> findUsages(Long id) {
        findBlock(id);
        String token = "\"reusableBlockId\":" + id;
        return manualBlockRepository.findByContentJsonContaining(token).stream()
                .filter(block -> block.getSection().getManualVersion().getManual().getDeletedAt() == null)
                .map(reusableBlockMapper::toUsageResponse)
                .toList();
    }

    private void applyRequest(ReusableBlockRequest request, ReusableBlock block) {
        block.setCode(request.code());
        block.setTitle(request.title());
        block.setProductCategory(request.productCategory());
        block.setProductCodes(request.productCodes());
        block.setContentJson(request.contentJson());
        block.setActive(request.active());
    }

    private ReusableBlock findBlock(Long id) {
        return reusableBlockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloque reutilizable no encontrado: " + id));
    }
}
