package com.dikoin.manuals.servicios;

import com.dikoin.manuals.dtos.product.ProductRequest;
import com.dikoin.manuals.dtos.product.ProductResponse;
import com.dikoin.manuals.dtos.product.ProductCategoryResponse;
import com.dikoin.manuals.dtos.product.ProductApplyImageRequest;
import com.dikoin.manuals.dtos.product.ProductDeactivateRequest;
import com.dikoin.manuals.dtos.product.ProductDeleteImpactResponse;
import com.dikoin.manuals.dtos.product.ProductFamilyResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {
    List<ProductResponse> findAll(String search);
    ProductResponse findById(Long id);
    List<ProductFamilyResponse> findFamilies();
    List<ProductCategoryResponse> findCategories();
    ProductResponse create(ProductRequest request);
    ProductResponse update(Long id, ProductRequest request);
    ProductDeleteImpactResponse deleteImpact(Long id);
    ProductResponse uploadImage(Long id, MultipartFile file);
    ProductResponse removeImage(Long id);
    ProductDeleteImpactResponse applyImageToManuals(Long id, ProductApplyImageRequest request);
    void delete(Long id, ProductDeactivateRequest request);
}
