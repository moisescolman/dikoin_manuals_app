package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.reusableblock.CreateReusableFragmentRequest;
import com.dikoin.manuals.dtos.reusableblock.InsertReusableFragmentRequest;
import com.dikoin.manuals.dtos.reusablefragment.*;
import com.dikoin.manuals.servicios.ReusableFragmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reusable-fragments")
@RequiredArgsConstructor
public class ReusableFragmentRestController {

    private final ReusableFragmentService reusableFragmentService;

    @GetMapping
    public List<ReusableFragmentResponse> findAll(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) String search
    ) {
        return reusableFragmentService.findAll(includeInactive, search);
    }

    @GetMapping("/{id}")
    public ReusableFragmentResponse findById(@PathVariable Long id) {
        return reusableFragmentService.findById(id);
    }

    @PostMapping
    public ReusableFragmentResponse create(@Valid @RequestBody ReusableFragmentRequest request) {
        return reusableFragmentService.create(request);
    }

    @PostMapping("/from-selection")
    public ReusableFragmentResponse createFromSelection(@Valid @RequestBody CreateReusableFragmentRequest request) {
        return reusableFragmentService.createFromSelection(request);
    }

    @PostMapping("/{id}/insert")
    public ReusableFragmentInsertResponse insertFragment(
            @PathVariable Long id,
            @Valid @RequestBody InsertReusableFragmentRequest request
    ) {
        return reusableFragmentService.insertFragment(id, request);
    }

    @PutMapping("/{id}")
    public ReusableFragmentResponse update(@PathVariable Long id, @Valid @RequestBody ReusableFragmentRequest request) {
        return reusableFragmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        reusableFragmentService.delete(id);
    }

    @GetMapping("/{id}/usages")
    public List<ReusableFragmentUsageResponse> findUsages(@PathVariable Long id) {
        return reusableFragmentService.findUsages(id);
    }
}
