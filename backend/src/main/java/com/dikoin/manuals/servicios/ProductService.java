package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.product.ProductRequest;
import com.dikoin.manuals.dtos.product.ProductResponse;
import com.dikoin.manuals.dtos.product.ProductCategoryResponse;
import com.dikoin.manuals.dtos.product.ProductFamilyResponse;

import java.util.List;

public interface ProductService {
    List<ProductResponse> findAll(String search);
    ProductResponse findById(Long id);
    List<ProductFamilyResponse> findFamilies();
    List<ProductCategoryResponse> findCategories();
    ProductResponse create(ProductRequest request);
    ProductResponse update(Long id, ProductRequest request);
    void delete(Long id);
}
