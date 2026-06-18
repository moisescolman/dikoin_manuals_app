package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.product.ProductRequest;
import com.dikoin.manuals.dtos.product.ProductResponse;
import com.dikoin.manuals.entidades.Product;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.ProductMapper;
import com.dikoin.manuals.repositorios.ProductRepository;
import com.dikoin.manuals.servicios.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    public List<ProductResponse> findAll(String search) {
        List<Product> products = search == null || search.isBlank()
                ? productRepository.findAll()
                : productRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(search, search);
        return products.stream().map(productMapper::toResponse).toList();
    }

    @Override
    public ProductResponse findById(Long id) {
        return productMapper.toResponse(getProduct(id));
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ApiException("Ya existe un producto con el codigo " + request.code());
        }
        Product product = productMapper.toEntity(request);
        product.setActive(request.active() == null || request.active());
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        productMapper.updateEntity(request, product);
        product.setActive(request.active() == null || request.active());
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        productRepository.delete(getProduct(id));
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
    }
}
