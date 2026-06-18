package com.dikoin.manuals.mappers;

import com.dikoin.manuals.dtos.template.TemplateRequest;
import com.dikoin.manuals.dtos.template.TemplateResponse;
import com.dikoin.manuals.dtos.template.TemplateVersionResponse;
import com.dikoin.manuals.entidades.Asset;
import com.dikoin.manuals.entidades.Template;
import com.dikoin.manuals.entidades.TemplateVersion;
import org.springframework.stereotype.Component;

@Component
public class TemplateMapper {

    public TemplateResponse toResponse(Template template) {
        Long logoAssetId = template.getLogoAsset() != null ? template.getLogoAsset().getId() : null;
        return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getCompanyName(),
                template.getContactEmail(),
                template.getContactPhone(),
                template.getWebsite(),
                template.getLogoPath(),
                logoAssetId,
                logoAssetId != null ? "/api/v1/assets/" + logoAssetId + "/file" : null,
                template.getHeaderConfigJson(),
                template.getFooterConfigJson(),
                template.isActive(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    public Template toEntity(TemplateRequest request, Asset logoAsset) {
        Template template = Template.builder().build();
        updateEntity(request, template, logoAsset);
        return template;
    }

    public void updateEntity(TemplateRequest request, Template template, Asset logoAsset) {
        template.setName(request.name());
        template.setDescription(request.description());
        template.setCompanyName(request.companyName());
        template.setContactEmail(request.contactEmail());
        template.setContactPhone(request.contactPhone());
        template.setWebsite(request.website());
        template.setLogoPath(request.logoPath());
        template.setLogoAsset(logoAsset);
        template.setHeaderConfigJson(request.headerConfigJson());
        template.setFooterConfigJson(request.footerConfigJson());
        template.setActive(request.active());
    }

    public TemplateVersionResponse toVersionResponse(TemplateVersion version) {
        return new TemplateVersionResponse(
                version.getId(),
                version.getTemplate().getId(),
                version.getVersionNumber(),
                version.getConfigJson(),
                version.isActive(),
                version.getNotes(),
                version.getCreatedAt()
        );
    }
}
