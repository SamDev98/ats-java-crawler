// src/main/java/com/atscrawler/service/ATSSlugValidator.java
package com.atscrawler.service;

import com.atscrawler.util.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Valida slugs do CSV automaticamente
 * Remove slugs 404 antes do sync
 */
@Service
public class ATSSlugValidator {
    private static final Logger log = LoggerFactory.getLogger(ATSSlugValidator.class);
    private final Http http;

    public ATSSlugValidator(Http http) {
        this.http = http;
    }

    public boolean isValidGreenhouseSlug(String slug) {
        String url = "https://boards-api.greenhouse.io/v1/boards/" + slug + "/jobs";
        String response = http.get(url);

        return response != null &&
                response.contains("\"jobs\"") &&
                !response.contains("\"status\":404");
    }

    public boolean isValidLeverSlug(String slug) {
        String url = "https://api.lever.co/v0/postings/" + slug + "?mode=json";
        String response = http.get(url);

        return response != null &&
                response.startsWith("[") &&
                !response.contains("\"ok\":false");
    }

    public boolean isValidWorkableSlug(String slug) {
        String url = "https://apply.workable.com/api/v3/accounts/" + slug + "/jobs";
        String response = http.get(url);

        return response != null &&
                response.contains("\"results\"") &&
                !response.contains("\"status\":404");
    }

    /**
     * Valida TODOS os slugs de uma lista
     * Retorna apenas os válidos
     */
    public List<String> filterValidSlugs(String atsType, List<String> slugs) {
        List<String> valid = new ArrayList<>();

        log.info("🔍 Validating {} {} slugs...", slugs.size(), atsType);

        for (String slug : slugs) {
            boolean isValid = switch (atsType) {
                case "Greenhouse" -> isValidGreenhouseSlug(slug);
                case "Lever" -> isValidLeverSlug(slug);
                case "Workable" -> isValidWorkableSlug(slug);
                default -> true; // Skip validation
            };

            if (isValid) {
                valid.add(slug);
                log.debug("  ✅ {}", slug);
            } else {
                log.warn("  ❌ {} (404 - removing)", slug);
            }

            // Rate limiting
            try { Thread.sleep(200); } catch (InterruptedException e) {}
        }

        log.info("✅ {} valid slugs ({} removed)", valid.size(), slugs.size() - valid.size());
        return valid;
    }
}