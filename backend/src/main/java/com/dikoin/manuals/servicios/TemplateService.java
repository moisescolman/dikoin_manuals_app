package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.template.TemplateRequest;
import com.dikoin.manuals.dtos.template.TemplateResponse;
import com.dikoin.manuals.dtos.template.TemplateVersionRequest;
import com.dikoin.manuals.dtos.template.TemplateVersionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TemplateService {
    List<TemplateResponse> findAll();
    TemplateResponse findActive();
    TemplateResponse create(TemplateRequest request);
    TemplateResponse update(Long id, TemplateRequest request);
    TemplateResponse activate(Long id);
    void delete(Long id);
    TemplateResponse uploadLogo(Long id, MultipartFile file);
    List<TemplateVersionResponse> findVersions(Long templateId);
    TemplateVersionResponse createVersion(Long templateId, TemplateVersionRequest request);
}
