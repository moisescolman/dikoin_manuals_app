package com.dikoin.manuals.mappers;

import com.dikoin.manuals.dtos.manual.*;
import com.dikoin.manuals.entidades.*;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ManualMapper {

    public ManualSummaryResponse toSummary(Manual manual, ManualVersion activeVersion) {
        return new ManualSummaryResponse(
                manual.getId(),
                manual.getCode(),
                manual.getTitle(),
                manual.getTitleEs(),
                manual.getTitleEn(),
                manual.getCategory(),
                manual.isEnabled(),
                manual.getDocumentType() != null ? manual.getDocumentType().getId() : null,
                manual.getDocumentType() != null ? manual.getDocumentType().getCode() : null,
                manual.getDocumentType() != null ? manual.getDocumentType().getName() : null,
                manual.getDocumentYear(),
                manual.getDocumentVersion(),
                manual.getLanguageCode(),
                manual.getProduct().getId(),
                manual.getProduct().getCode(),
                manual.getProduct().getName(),
                activeVersion != null ? activeVersion.getId() : null,
                activeVersion != null ? activeVersion.getVersionNumber() : null,
                activeVersion != null ? activeVersion.getStatus() : null,
                activeVersion != null && activeVersion.isEsReady(),
                activeVersion != null && activeVersion.isEnReady(),
                manual.getUpdatedAt(),
                manual.getDeletedAt()
        );
    }

    public ManualDetailResponse toDetail(Manual manual, List<ManualVersion> versions, ManualVersion activeVersion) {
        return new ManualDetailResponse(
                manual.getId(),
                manual.getCode(),
                manual.getTitle(),
                manual.getTitleEs(),
                manual.getTitleEn(),
                manual.getCategory(),
                manual.isEnabled(),
                manual.getDocumentType() != null ? manual.getDocumentType().getId() : null,
                manual.getDocumentType() != null ? manual.getDocumentType().getCode() : null,
                manual.getDocumentType() != null ? manual.getDocumentType().getName() : null,
                manual.getDocumentYear(),
                manual.getDocumentVersion(),
                manual.getLanguageCode(),
                manual.getProduct().getId(),
                manual.getProduct().getCode(),
                manual.getProduct().getName(),
                manual.getCreatedAt(),
                manual.getUpdatedAt(),
                manual.getDeletedAt(),
                activeVersion != null ? toVersionResponse(activeVersion, true) : null,
                versions.stream().map(version -> toVersionResponse(version, false)).toList()
        );
    }

    public ManualVersionResponse toVersionResponse(ManualVersion version, boolean includeSections) {
        List<ManualSectionResponse> sections = includeSections
                ? version.getSections().stream()
                .sorted(Comparator.comparing(ManualSection::getSortOrder))
                .map(this::toSectionResponse)
                .toList()
                : List.of();

        return new ManualVersionResponse(
                version.getId(),
                version.getVersionNumber(),
                version.getStatus(),
                version.isActive(),
                version.isEsReady(),
                version.isEnReady(),
                version.getChangeNotes(),
                version.getCreatedAt(),
                version.getUpdatedAt(),
                version.getPublishedAt(),
                sections
        );
    }

    public ManualSectionResponse toSectionResponse(ManualSection section) {
        return new ManualSectionResponse(
                section.getId(),
                section.getSortOrder(),
                section.getSectionNumber(),
                section.getTitleEs(),
                section.getTitleEn(),
                section.getCompletionStatus(),
                section.getBlocks().stream()
                        .sorted(Comparator.comparing(ManualBlock::getSortOrder))
                        .map(this::toBlockResponse)
                        .toList()
        );
    }

    public ManualBlockResponse toBlockResponse(ManualBlock block) {
        return new ManualBlockResponse(
                block.getId(),
                block.getSortOrder(),
                block.getBlockType(),
                block.getLanguageCode(),
                block.getContentJson()
        );
    }
}
