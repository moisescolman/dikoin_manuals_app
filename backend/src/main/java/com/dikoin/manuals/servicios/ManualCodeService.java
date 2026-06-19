package com.dikoin.manuals.servicios;

import com.dikoin.manuals.entidades.DocumentType;
import com.dikoin.manuals.entidades.Product;
import org.springframework.stereotype.Service;

@Service
public class ManualCodeService {

    public String generate(DocumentType documentType, Product product, String year, String version, String languageCode) {
        if (documentType == null || product == null || isBlank(year) || isBlank(version) || isBlank(languageCode)) {
            return null;
        }
        return documentType.getCode().trim().toUpperCase()
                + "-"
                + product.getCode().trim().toUpperCase()
                + "-"
                + twoDigits(year)
                + twoDigits(version)
                + "["
                + languageCode.trim().toUpperCase()
                + "]";
    }

    public String twoDigits(String value) {
        String cleaned = value == null ? "" : value.replaceAll("\\D", "");
        if (cleaned.length() == 1) {
            return "0" + cleaned;
        }
        if (cleaned.length() > 2) {
            return cleaned.substring(cleaned.length() - 2);
        }
        return cleaned;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
