package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.reusableblock.*;
import com.dikoin.manuals.enums.ReusableType;
import com.dikoin.manuals.servicios.ReusableBlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reusable-blocks")
@RequiredArgsConstructor
public class ReusableBlockRestController {

    private final ReusableBlockService reusableBlockService;

    @GetMapping
    public List<ReusableBlockResponse> findAll(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) ReusableType type
    ) {
        return reusableBlockService.findAll(includeInactive, type);
    }

    @GetMapping("/{id}")
    public ReusableBlockResponse findById(@PathVariable Long id) {
        return reusableBlockService.findById(id);
    }

    @PostMapping
    public ReusableBlockResponse create(@Valid @RequestBody ReusableBlockRequest request) {
        return reusableBlockService.create(request);
    }

    @PostMapping("/fragments")
    public ReusableBlockResponse createFragment(@Valid @RequestBody CreateReusableFragmentRequest request) {
        return reusableBlockService.createFragment(request);
    }

    @PostMapping("/{id}/insert")
    public ReusableFragmentInsertResponse insertFragment(
            @PathVariable Long id,
            @Valid @RequestBody InsertReusableFragmentRequest request
    ) {
        return reusableBlockService.insertFragment(id, request);
    }

    @PutMapping("/{id}")
    public ReusableBlockResponse update(@PathVariable Long id, @Valid @RequestBody ReusableBlockRequest request) {
        return reusableBlockService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        reusableBlockService.delete(id);
    }

    @GetMapping("/{id}/usages")
    public List<ReusableBlockUsageResponse> findUsages(@PathVariable Long id) {
        return reusableBlockService.findUsages(id);
    }
}
