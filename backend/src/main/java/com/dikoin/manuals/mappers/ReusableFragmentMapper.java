package com.dikoin.manuals.mappers;

import com.dikoin.manuals.dtos.reusablefragment.ReusableFragmentResponse;
import com.dikoin.manuals.dtos.reusablefragment.ReusableFragmentUsageResponse;
import com.dikoin.manuals.entidades.Manual;
import com.dikoin.manuals.entidades.ManualBlock;
import com.dikoin.manuals.entidades.ManualSection;
import com.dikoin.manuals.entidades.ReusableFragment;
import org.springframework.stereotype.Component;

@Component
public class ReusableFragmentMapper {

    public ReusableFragmentResponse toResponse(ReusableFragment fragment) {
        return new ReusableFragmentResponse(
                fragment.getId(),
                fragment.getCode(),
                fragment.getTitle(),
                fragment.getDescription(),
                fragment.getProductCategory(),
                fragment.getProductCodes(),
                fragment.getContentJson(),
                fragment.isActive(),
                fragment.getCreatedAt(),
                fragment.getUpdatedAt()
        );
    }

    public ReusableFragmentUsageResponse toUsageResponse(ManualBlock block) {
        ManualSection section = block.getSection();
        Manual manual = section.getManualVersion().getManual();
        return new ReusableFragmentUsageResponse(
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
