package com.dikoin.manuals.rest;

import com.dikoin.manuals.dtos.search.SearchSuggestionResponse;
import com.dikoin.manuals.repositorios.ManualRepository;
import com.dikoin.manuals.repositorios.ProductRepository;
import com.dikoin.manuals.repositorios.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchRestController {

    private final ManualRepository manualRepository;
    private final ProductRepository productRepository;
    private final TemplateRepository templateRepository;

    @GetMapping("/suggestions")
    public List<SearchSuggestionResponse> suggestions(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }

        List<SearchSuggestionResponse> results = new ArrayList<>();

        manualRepository.searchActive(q).stream().limit(8).forEach(manual -> results.add(new SearchSuggestionResponse(
                "MANUAL",
                manual.getId(),
                manual.getCode(),
                manual.getTitle(),
                "/manuales/" + manual.getId()
        )));

        productRepository.search(q).stream().limit(5).forEach(product -> results.add(new SearchSuggestionResponse(
                "PRODUCT",
                product.getId(),
                product.getCode(),
                product.getName(),
                "/productos"
        )));

        templateRepository.findByNameContainingIgnoreCaseOrderByUpdatedAtDesc(q).stream().limit(5).forEach(template -> results.add(new SearchSuggestionResponse(
                "TEMPLATE",
                template.getId(),
                template.getName(),
                template.isActive() ? "Plantilla activa" : "Plantilla",
                "/plantillas"
        )));

        return results.stream().limit(12).toList();
    }
}
