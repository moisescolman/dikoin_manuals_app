package com.dikoin.manuals.servicios.impl;

import com.dikoin.manuals.exceptions.ApiException;
import com.dikoin.manuals.servicios.GeminiTranslationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GeminiTranslationServiceImpl implements GeminiTranslationService {

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";

    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    @Value("${gemini.enabled:true}")
    private boolean enabled;

    private final RestClient restClient = RestClient.builder()
            .baseUrl(GEMINI_BASE_URL)
            .build();

    @Override
    public Map<String, String> translateToEnglish(List<TranslationTextItem> items) {
        if (!enabled) {
            throw new ApiException("La traduccion automatica no esta configurada. Gemini esta deshabilitado.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiException("La traduccion automatica no esta configurada. Falta GEMINI_API_KEY.");
        }
        if (items == null || items.isEmpty()) {
            return Map.of();
        }

        RuntimeException firstFailure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return requestTranslation(items);
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }
        throw new ApiException("No se ha podido completar la traduccion automatica. Revisa la configuracion de Gemini API.", firstFailure);
    }

    private Map<String, String> requestTranslation(List<TranslationTextItem> items) {
        String inputJson = toJson(items.stream()
                .map(item -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", item.id());
                    node.put("blockType", item.blockType().name());
                    node.put("translatableText", item.translatableText());
                    return node;
                })
                .toList());

        String prompt = """
                Eres un traductor tecnico especializado en manuales de laboratorio, equipos didacticos, hidraulica, electricidad, energia y documentacion tecnica.

                Traduce del espanol al ingles el contenido proporcionado.

                Reglas obligatorias:
                - Devuelve unicamente JSON valido.
                - No anadas explicaciones.
                - No uses Markdown.
                - No cambies IDs.
                - No cambies el orden de los bloques.
                - No elimines items.
                - No anadas items.
                - No cambies HTML salvo el texto visible.
                - No cambies formulas.
                - No cambies unidades.
                - No cambies numeros.
                - No traduzcas codigos de producto.
                - No traduzcas nombres de marca como DIKOIN.
                - No traduzcas referencias como FL 01.1, EN 05.1, TC 01.1, DMP, MP-VAR075, MP-THM01.
                - Manten los terminos tecnicos con traduccion natural al ingles.
                - Manten el tono de manual tecnico.
                - Conserva saltos de linea cuando sean relevantes.
                - Traduce solo campos translatableText.
                - Devuelve por cada item el mismo id y el campo translatedText.

                Entrada:
                %s
                """.formatted(inputJson);

        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );

        String response = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-goog-api-key", apiKey)
                .body(request)
                .retrieve()
                .body(String.class);

        return parseAndValidate(items, response);
    }

    private Map<String, String> parseAndValidate(List<TranslationTextItem> items, String response) {
        String text = extractCandidateText(response);
        JsonNode translated = parseJson(cleanJsonText(text));
        if (!translated.isArray()) {
            throw new ApiException("Gemini devolvio una respuesta no valida para traduccion.");
        }

        Set<String> expectedIds = new HashSet<>();
        items.forEach(item -> expectedIds.add(item.id()));
        Set<String> receivedIds = new HashSet<>();
        Map<String, String> result = new HashMap<>();

        for (JsonNode node : translated) {
            String id = node.path("id").asText("");
            if (!expectedIds.contains(id)) {
                throw new ApiException("Gemini devolvio un ID no solicitado.");
            }
            String translatedText = node.path("translatedText").asText(null);
            if (translatedText == null) {
                throw new ApiException("Gemini omitio translatedText para un item.");
            }
            receivedIds.add(id);
            result.put(id, translatedText);
        }

        if (!receivedIds.equals(expectedIds)) {
            throw new ApiException("Gemini no devolvio todos los IDs solicitados.");
        }
        return result;
    }

    private String extractCandidateText(String response) {
        JsonNode root = parseJson(response);
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.size() == 0) {
            throw new ApiException("Gemini no devolvio contenido traducido.");
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : parts) {
            String value = part.path("text").asText("");
            if (!value.isBlank()) {
                builder.append(value);
            }
        }
        if (builder.isEmpty()) {
            throw new ApiException("Gemini devolvio contenido vacio.");
        }
        return builder.toString();
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new ApiException("La respuesta de Gemini no es JSON valido.", exception);
        }
    }

    private String cleanJsonText(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException("No se pudo preparar el lote de traduccion.", exception);
        }
    }
}
