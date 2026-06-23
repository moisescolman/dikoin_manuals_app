package com.dikoin.manuals.mappers;

import com.dikoin.manuals.dtos.product.ProductCategoryResponse;
import com.dikoin.manuals.dtos.product.ProductFamilyResponse;
import com.dikoin.manuals.dtos.product.ProductResponse;
import com.dikoin.manuals.entidades.Product;
import com.dikoin.manuals.entidades.ProductCategory;
import com.dikoin.manuals.entidades.ProductFamily;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        ProductFamily family = product.getProductFamily();
        List<ProductCategoryResponse> categories = product.getCategories().stream()
                .sorted(Comparator.comparing(ProductCategory::getCode, String.CASE_INSENSITIVE_ORDER))
                .map(this::toCategoryResponse)
                .toList();
        List<Long> categoryIds = categories.stream().map(ProductCategoryResponse::id).toList();
        String categoryCodes = categories.isEmpty()
                ? product.getCategoryCodes()
                : categories.stream().map(ProductCategoryResponse::code).collect(Collectors.joining(", "));
        String categoryLabel = categories.isEmpty()
                ? product.getCategory()
                : categories.stream()
                    .map(category -> category.code() + " - " + category.nameEs())
                    .collect(Collectors.joining(" | "));

        return new ProductResponse(
                product.getId(),
                product.getCode(),
                firstText(product.getName(), product.getNameEs()),
                firstText(product.getNameEs(), product.getName()),
                product.getNameEn(),
                family != null ? family.getId() : null,
                firstText(product.getFamilyCode(), family != null ? family.getCode() : null),
                firstText(product.getFamily(), family != null ? family.getCode() + " - " + family.getNameEs() : null),
                family != null ? toFamilyResponse(family) : null,
                categoryIds,
                categoryCodes,
                categoryLabel,
                categories,
                firstText(product.getDescription(), product.getDescriptionEs()),
                firstText(product.getDescriptionEs(), product.getDescription()),
                product.getDescriptionEn(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public ProductFamilyResponse toFamilyResponse(ProductFamily family) {
        return new ProductFamilyResponse(family.getId(), family.getCode(), family.getNameEs(), family.getNameEn());
    }

    public ProductCategoryResponse toCategoryResponse(ProductCategory category) {
        return new ProductCategoryResponse(category.getId(), category.getCode(), category.getNameEs(), category.getNameEn());
    }

    private String firstText(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }
}
