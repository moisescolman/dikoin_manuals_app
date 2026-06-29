package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.product.ProductCategoryResponse;
import com.dikoin.manuals.dtos.product.ProductApplyImageRequest;
import com.dikoin.manuals.dtos.product.ProductDeactivateRequest;
import com.dikoin.manuals.dtos.product.ProductDeleteImpactResponse;
import com.dikoin.manuals.dtos.product.ProductFamilyResponse;
import com.dikoin.manuals.dtos.product.ProductRequest;
import com.dikoin.manuals.dtos.product.ProductResponse;
import com.dikoin.manuals.servicios.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductRestController {

    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> findAll(@RequestParam(required = false) String search) {
        return productService.findAll(search);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @GetMapping("/families")
    public List<ProductFamilyResponse> findFamilies() {
        return productService.findFamilies();
    }

    @GetMapping("/categories")
    public List<ProductCategoryResponse> findCategories() {
        return productService.findCategories();
    }

    @PostMapping
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @GetMapping("/{id}/delete-impact")
    public ProductDeleteImpactResponse deleteImpact(@PathVariable Long id) {
        return productService.deleteImpact(id);
    }

    @PostMapping("/{id}/image")
    public ProductResponse uploadImage(@PathVariable Long id, @RequestParam MultipartFile file) {
        return productService.uploadImage(id, file);
    }

    @DeleteMapping("/{id}/image")
    public ProductResponse removeImage(@PathVariable Long id) {
        return productService.removeImage(id);
    }

    @PostMapping("/{id}/image/apply")
    public ProductDeleteImpactResponse applyImageToManuals(@PathVariable Long id, @RequestBody ProductApplyImageRequest request) {
        return productService.applyImageToManuals(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, @RequestBody(required = false) ProductDeactivateRequest request) {
        productService.delete(id, request == null ? new ProductDeactivateRequest(List.of()) : request);
    }
}
