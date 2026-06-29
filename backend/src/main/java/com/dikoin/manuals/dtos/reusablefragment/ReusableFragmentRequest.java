package com.dikoin.manuals.dtos.reusablefragment;

public record ReusableFragmentRequest(
        String code,
        String title,
        String description,
        String productCategory,
        String productCodes,
        String contentJson,
        boolean active
) {
}
