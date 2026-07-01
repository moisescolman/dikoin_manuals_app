package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.template.TemplateRequest;
import com.dikoin.manuals.dtos.template.TemplateResponse;
import com.dikoin.manuals.dtos.template.TemplateVersionRequest;
import com.dikoin.manuals.dtos.template.TemplateVersionResponse;
import com.dikoin.manuals.servicios.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateRestController {

    private final TemplateService templateService;

    @GetMapping
    public List<TemplateResponse> findAll() {
        return templateService.findAll();
    }

    @GetMapping("/active")
    public TemplateResponse findActive() {
        return templateService.findActive();
    }

    @PostMapping
    public TemplateResponse create(@Valid @RequestBody TemplateRequest request) {
        return templateService.create(request);
    }

    @PutMapping("/{id}")
    public TemplateResponse update(@PathVariable Long id, @Valid @RequestBody TemplateRequest request) {
        return templateService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public TemplateResponse activate(@PathVariable Long id) {
        return templateService.activate(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        templateService.delete(id);
    }

    @PostMapping("/{id}/logo")
    public TemplateResponse uploadLogo(@PathVariable Long id, @RequestParam MultipartFile file) {
        return templateService.uploadLogo(id, file);
    }

    @GetMapping("/{id}/versions")
    public List<TemplateVersionResponse> findVersions(@PathVariable Long id) {
        return templateService.findVersions(id);
    }

    @PostMapping("/{id}/versions")
    public TemplateVersionResponse createVersion(@PathVariable Long id, @Valid @RequestBody TemplateVersionRequest request) {
        return templateService.createVersion(id, request);
    }
}
