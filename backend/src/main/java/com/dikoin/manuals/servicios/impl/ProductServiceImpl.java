package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.dtos.product.ProductRequest;
import com.dikoin.manuals.dtos.product.ProductResponse;
import com.dikoin.manuals.dtos.product.ProductCategoryResponse;
import com.dikoin.manuals.dtos.product.ProductFamilyResponse;
import com.dikoin.manuals.entidades.Product;
import com.dikoin.manuals.entidades.ProductCategory;
import com.dikoin.manuals.entidades.ProductFamily;
import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.exceptions.ResourceNotFoundException;
import com.dikoin.manuals.mappers.ProductMapper;
import com.dikoin.manuals.repositorios.ProductCategoryRepository;
import com.dikoin.manuals.repositorios.ProductFamilyRepository;
import com.dikoin.manuals.repositorios.ProductRepository;
import com.dikoin.manuals.servicios.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductFamilyRepository familyRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(String search) {
        List<Product> products = search == null || search.isBlank()
                ? productRepository.findAllWithTaxonomy()
                : productRepository.search(search.trim());
        return products.stream().map(productMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return productMapper.toResponse(getProduct(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductFamilyResponse> findFamilies() {
        return familyRepository.findAllByOrderByCodeAsc().stream()
                .map(productMapper::toFamilyResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> findCategories() {
        return categoryRepository.findAllByOrderByCodeAsc().stream()
                .map(productMapper::toCategoryResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ApiException("Ya existe un producto con el codigo " + request.code());
        }
        Product product = new Product();
        applyRequest(product, request);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = getProduct(id);
        applyRequest(product, request);
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

    private void applyRequest(Product product, ProductRequest request) {
        ProductFamily family = resolveFamily(request);
        Set<ProductCategory> categories = resolveCategories(request);
        String nameEs = titleCase(firstText(request.nameEs(), request.name()));
        String nameEn = titleCase(request.nameEn());
        String familyLabel = family != null
                ? family.getCode() + " - " + family.getNameEs()
                : request.family();
        String categoryLabel = categories.isEmpty()
                ? request.category()
                : categories.stream()
                    .map(category -> category.getCode() + " - " + category.getNameEs())
                    .collect(Collectors.joining(" | "));
        String categoryCodes = categories.isEmpty()
                ? request.categoryCodes()
                : categories.stream().map(ProductCategory::getCode).collect(Collectors.joining(", "));

        product.setCode(request.code().trim());
        product.setName(nameEs);
        product.setNameEs(nameEs);
        product.setNameEn(nameEn);
        product.setProductFamily(family);
        product.setFamilyCode(family != null ? family.getCode() : firstText(request.familyCode(), codeFromLabel(request.family())));
        product.setFamily(familyLabel);
        product.getCategories().clear();
        product.getCategories().addAll(categories);
        product.setCategoryCodes(categoryCodes);
        product.setCategory(categoryLabel);
        product.setDescription(firstText(request.descriptionEs(), request.description()));
        product.setDescriptionEs(firstText(request.descriptionEs(), request.description()));
        product.setDescriptionEn(request.descriptionEn());
        product.setActive(request.active() == null || request.active());
    }

    private ProductFamily resolveFamily(ProductRequest request) {
        if (request.familyId() != null) {
            return familyRepository.findById(request.familyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Familia no encontrada: " + request.familyId()));
        }
        String code = firstText(request.familyCode(), codeFromLabel(request.family()));
        if (code == null) return null;
        return familyRepository.findByCodeIgnoreCase(code).orElse(null);
    }

    private Set<ProductCategory> resolveCategories(ProductRequest request) {
        Set<ProductCategory> categories = new LinkedHashSet<>();
        if (request.categoryIds() != null && !request.categoryIds().isEmpty()) {
            request.categoryIds().forEach(id -> categories.add(categoryRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada: " + id))));
            return categories;
        }
        String codes = request.categoryCodes();
        if (codes == null || codes.isBlank()) {
            codes = request.category();
        }
        if (codes == null) return categories;
        for (String raw : codes.split("[,|]")) {
            String code = codeFromLabel(raw);
            if (code != null) {
                categoryRepository.findByCodeIgnoreCase(code).ifPresent(categories::add);
            }
        }
        return categories;
    }

    private String firstText(String first, String fallback) {
        return first != null && !first.isBlank() ? first.trim() : (fallback != null && !fallback.isBlank() ? fallback.trim() : null);
    }

    private String codeFromLabel(String label) {
        if (label == null || label.isBlank()) return null;
        String trimmed = label.trim();
        String[] parts = trimmed.split("\\s*[–-]\\s*", 2);
        return parts[0].trim();
    }

    private String titleCase(String text) {
        if (text == null || text.isBlank()) return text;
        Set<String> lowercaseWords = Set.of("a", "al", "con", "de", "del", "e", "el", "en", "la", "las", "los", "o", "para", "por", "sobre", "un", "una", "y");
        String[] originalWords = text.trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < originalWords.length; i++) {
            String originalWord = originalWords[i];
            String word = originalWord.toLowerCase(Locale.ROOT);
            if (i > 0) result.append(' ');
            if (originalWord.matches(".*\\d.*") || (originalWord.length() <= 3 && originalWord.equals(originalWord.toUpperCase(Locale.ROOT)))) {
                result.append(originalWord);
            } else if (i > 0 && lowercaseWords.contains(word)) {
                result.append(word);
            } else {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return result.toString();
    }
}
