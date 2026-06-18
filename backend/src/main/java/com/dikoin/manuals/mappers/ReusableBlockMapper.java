package com.dikoin.manuals.mappers;

import com.dikoin.manuals.dtos.reusableblock.ReusableBlockResponse;
import com.dikoin.manuals.dtos.reusableblock.ReusableBlockUsageResponse;
import com.dikoin.manuals.entidades.Manual;
import com.dikoin.manuals.entidades.ManualBlock;
import com.dikoin.manuals.entidades.ManualSection;
import com.dikoin.manuals.entidades.ReusableBlock;
import org.springframework.stereotype.Component;

@Component
public class ReusableBlockMapper {

    public ReusableBlockResponse toResponse(ReusableBlock block) {
        return new ReusableBlockResponse(
                block.getId(),
                block.getCode(),
                block.getTitle(),
                block.getProductCategory(),
                block.getProductCodes(),
                block.getContentJson(),
                block.isActive(),
                block.getCreatedAt(),
                block.getUpdatedAt()
        );
    }

    public ReusableBlockUsageResponse toUsageResponse(ManualBlock block) {
        ManualSection section = block.getSection();
        Manual manual = section.getManualVersion().getManual();
        return new ReusableBlockUsageResponse(
                manual.getId(),
                manual.getCode(),
                manual.getTitle(),
                manual.getProduct().getCode(),
                section.getId(),
                section.getSectionNumber(),
                section.getTitleEs(),
                block.getId()
        );
    }
}
