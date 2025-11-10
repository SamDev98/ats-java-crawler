package com.atscrawler.service;

import com.atscrawler.util.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for validating ATS company slugs before crawling.
 * Automatically removes invalid slugs (404 responses) from configuration.
 *
 * <p>Supports validation for:
 * <ul>
 *   <li>Greenhouse - JSON API endpoint validation</li>
 *   <li>Lever - JSON API endpoint validation</li>
 *   <li>Workable - JSON API v3 endpoint validation</li>
 * </ul>
 *
 * <p>Validation includes:
 * <ul>
 *   <li>HTTP response validation (non-null, non-empty)</li>
 *   <li>JSON structure validation</li>
 *   <li>Error response detection (404, API errors)</li>
 * </ul>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Service
public class ATSSlugValidator {
    private static final Logger log = LoggerFactory.getLogger(ATSSlugValidator.class);
    private final Http http;

    /**
     * Constructs a new ATSSlugValidator.
     *
     * @param http HTTP client for making validation requests
     */
    public ATSSlugValidator(Http http) {
        this.http = http;
    }

    /**
     * Validates a Greenhouse company slug by checking the API endpoint.
     *
     * <p>Endpoint: <a href="https://boards-api.greenhouse.io/v1/boards/">...</a>{slug}/jobs
     *
     * @param slug the company slug to validate
     * @return true if slug is valid (returns jobs array), false otherwise
     */
    public boolean isValidGreenhouseSlug(String slug) {
        String url = "https://boards-api.greenhouse.io/v1/boards/" + slug + "/jobs";
        String response = http.get(url);

        return response != null &&
                response.contains("\"jobs\"") &&
                !response.contains("\"status\":404");
    }

    /**
     * Validates a Lever company slug by checking the API endpoint.
     *
     * <p>Endpoint: <a href="https://api.lever.co/v0/postings/">...</a>{slug}?mode=json
     *
     * @param slug the company slug to validate
     * @return true if slug is valid (returns JSON array), false otherwise
     */
    public boolean isValidLeverSlug(String slug) {
        String url = "https://api.lever.co/v0/postings/" + slug + "?mode=json";
        String response = http.get(url);

        return response != null &&
                response.startsWith("[") &&
                !response.contains("\"ok\":false");
    }

    /**
     * Validates a Workable company slug by checking the API v3 endpoint.
     *
     * <p>Endpoint: <a href="https://apply.workable.com/api/v3/accounts/">...</a>{slug}/jobs
     *
     * @param slug the company slug to validate
     * @return true if slug is valid (returns results array), false otherwise
     */
    public boolean isValidWorkableSlug(String slug) {
        String url = "https://apply.workable.com/api/v3/accounts/" + slug + "/jobs";
        String response = http.get(url);

        return response != null &&
                response.contains("\"results\"") &&
                !response.contains("\"status\":404");
    }

    /**
     * Validates all slugs in a list and returns only valid ones.
     *
     * <p>Applies rate limiting (200ms delay) between requests to avoid API throttling.
     * Logs validation results for monitoring.
     *
     * @param atsType the ATS platform type (Greenhouse, Lever, Workable)
     * @param slugs list of slugs to validate
     * @return filtered list containing only valid slugs
     */
    public List<String> filterValidSlugs(String atsType, List<String> slugs) {
        List<String> valid = new ArrayList<>();

        log.info("🔍 Validating {} {} slugs...", slugs.size(), atsType);

        for (String slug : slugs) {
            boolean isValid = switch (atsType) {
                case "Greenhouse" -> isValidGreenhouseSlug(slug);
                case "Lever" -> isValidLeverSlug(slug);
                case "Workable" -> isValidWorkableSlug(slug);
                default -> true; // Skip validation for unsupported ATS
            };

            if (isValid) {
                valid.add(slug);
                log.debug("  ✅ {}", slug);
            } else {
                log.warn("  ❌ {} (404 - removing)", slug);
            }

            // Rate limiting to avoid API throttling
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("✅ {} valid slugs ({} removed)", valid.size(), slugs.size() - valid.size());
        return valid;
    }
}