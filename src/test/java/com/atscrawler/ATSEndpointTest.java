package com.atscrawler;

import com.atscrawler.util.Http;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manual integration test that validates connectivity and response formats
 * for all supported ATS (Applicant Tracking System) endpoints.
 *
 * <p>Checks each configured ATS provider for:
 * <ul>
 *   <li>✅ Reachable JSON APIs (expected for Greenhouse, Lever, etc.)</li>
 *   <li>⚠️ HTML-only pages (e.g., Ashby)</li>
 *   <li>❌ Inaccessible or invalid endpoints</li>
 * </ul>
 *
 * <p>Each response is classified as JSON, HTML, or Unknown.
 * Intended for debugging API health, not for automated CI.
 *
 * @since 0.4.2
 */
public class ATSEndpointTest {

    private final Http http = new Http(10000, 15000, 1);

    /**
     * Tests connectivity and response format for all main ATS endpoints.
     */
    @Test
    public void testAllATSEndpoints() {
        Map<String, List<String>> endpoints = new HashMap<>();

        // Tier 1: Primary active ATS integrations
        endpoints.put("Greenhouse", List.of("https://boards-api.greenhouse.io/v1/boards/vercel/jobs"));
        endpoints.put("Lever", List.of("https://api.lever.co/v0/postings/zapier?mode=json"));
        endpoints.put("Workable", List.of("https://apply.workable.com/api/v1/widget/accounts/canonical"));
        endpoints.put("Recruitee", List.of("https://hotjar.recruitee.com/api/offers/"));
        endpoints.put("Teamtailor", List.of("https://career.teamtailor.com/jobs.rss"));
        endpoints.put("Ashby", List.of("https://jobs.ashbyhq.com/linear")); // HTML-based job listings

        System.out.println("====================================");
        System.out.println("🧪 TESTING ALL ATS ENDPOINTS");
        System.out.println("====================================\n");

        for (Map.Entry<String, List<String>> entry : endpoints.entrySet()) {
            String ats = entry.getKey();
            System.out.println("📌 " + ats);
            for (String url : entry.getValue()) {
                testEndpoint(url);
            }
            System.out.println();
        }

        System.out.println("====================================");
        System.out.println("✅ TEST COMPLETED");
        System.out.println("====================================");
    }

    /**
     * Performs a simple GET request to the given ATS endpoint and classifies its response type.
     *
     * @param url the ATS endpoint URL
     */
    private void testEndpoint(String url) {
        String body = http.get(url);

        if (body == null || body.isBlank()) {
            System.out.println("  ❌ FAILED: " + url);
        } else if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
            System.out.println("  ✅ JSON API: " + url);
        } else if (body.contains("<html") || body.contains("<!DOCTYPE")) {
            System.out.println("  ⚠️ HTML PAGE: " + url);
        } else {
            System.out.println("  ❓ UNKNOWN: " + url);
        }
    }
}
