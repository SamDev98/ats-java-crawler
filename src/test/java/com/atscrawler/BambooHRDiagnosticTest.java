// src/test/java/com/atscrawler/BambooHRDiagnosticTest.java
package com.atscrawler;

import com.atscrawler.util.Http;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🔍 BambooHR Diagnostic Tool
 * <p>
 * Analyzes ALL 44 BambooHR companies from CSV to:
 * 1. Detect redirects to other ATS (Greenhouse, Lever, Ashby)
 * 2. Identify companies with active BambooHR careers pages
 * 3. Find companies with no public careers page (404)
 * <p>
 * Run manually: mvn test -Dtest=BambooHRDiagnosticTest#diagnoseBambooHRCompanies
 */
public class BambooHRDiagnosticTest {

    private static final Logger log = LoggerFactory.getLogger(BambooHRDiagnosticTest.class);
    private final Http http = new Http(10000, 15000, 1);

    @Test
    @Disabled("Manual diagnostic - hits real endpoints")
    public void diagnoseBambooHRCompanies() {
        // ✅ Lista COMPLETA extraída do CSV (doc 42)
        String[] bambooCompanies = {
                "brex", "billcom", "salesforce", "hubspot", "slack",
                "asana", "mondaycom", "airtable", "atlassian", "zendesk",
                "datadog", "databricks", "rippling", "instacart", "grubhub",
                "scaleai", "epicgames", "discord", "opensea", "ea",
                "twitch", "wildlifestudios", "supercell", "zynga", "deel",
                "pipe", "flexport", "pagerduty", "coinbase", "gympass",
                "pipefy", "openai", "anthropic", "datarobot", "elastic",
                "hotjar", "adyen", "plaid", "wise"
        };

        System.out.println("\n" + "=".repeat(70));
        System.out.println("🔍 BAMBOOHR DIAGNOSTIC REPORT");
        System.out.println("=".repeat(70));
        System.out.println("Testing " + bambooCompanies.length + " companies...\n");

        int totalCompanies = bambooCompanies.length;
        int redirectToGreenhouse = 0;
        int redirectToLever = 0;
        int redirectToAshby = 0;
        int hasBambooHR = 0;
        int notFound = 0;
        int errors = 0;

        for (String company : bambooCompanies) {
            String url = "https://" + company + ".bamboohr.com/careers/";

            try {
                String html = http.get(url);

                if (html == null || html.isBlank()) {
                    System.out.println("❌ " + company + " - NULL response");
                    errors++;
                } else if (html.contains("404") || html.contains("Not Found")) {
                    System.out.println("❌ " + company + " - 404 Not Found (no careers page)");
                    notFound++;
                } else if (html.contains("greenhouse.io")) {
                    System.out.println("🔀 " + company + " - REDIRECTS to Greenhouse");
                    redirectToGreenhouse++;
                } else if (html.contains("lever.co")) {
                    System.out.println("🔀 " + company + " - REDIRECTS to Lever");
                    redirectToLever++;
                } else if (html.contains("ashbyhq.com")) {
                    System.out.println("🔀 " + company + " - REDIRECTS to Ashby");
                    redirectToAshby++;
                } else if (html.contains("workable.com")) {
                    System.out.println("🔀 " + company + " - REDIRECTS to Workable");
                } else if (html.contains("job") || html.contains("career") ||
                        html.contains("position") || html.contains("opening")) {
                    System.out.println("✅ " + company + " - HAS BambooHR careers page");
                    hasBambooHR++;

                    // ✅ BONUS: Count job postings
                    int jobCount = countJobs(html);
                    if (jobCount > 0) {
                        System.out.println("   └─ Found ~" + jobCount + " job postings");
                    }
                } else {
                    System.out.println("⚠️  " + company + " - Unknown page structure");
                }

            } catch (Exception e) {
                System.out.println("💥 " + company + " - Error: " + e.getMessage());
                errors++;
            }

            // Rate limit protection
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // ✅ SUMMARY REPORT
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 DIAGNOSTIC SUMMARY");
        System.out.println("=".repeat(70));
        System.out.println("Total companies:           " + totalCompanies);
        System.out.println("├─ 🔀 Redirect to Greenhouse: " + redirectToGreenhouse);
        System.out.println("├─ 🔀 Redirect to Lever:      " + redirectToLever);
        System.out.println("├─ 🔀 Redirect to Ashby:      " + redirectToAshby);
        System.out.println("├─ ✅ Active BambooHR:        " + hasBambooHR);
        System.out.println("├─ ❌ No careers page (404):  " + notFound);
        System.out.println("└─ 💥 Errors:                 " + errors);
        System.out.println("\n📈 POTENTIAL IMPROVEMENT:");

        int capturable = redirectToGreenhouse + redirectToLever + redirectToAshby + hasBambooHR;
        int percentage = (capturable * 100) / totalCompanies;

        System.out.println("   " + capturable + "/" + totalCompanies +
                " companies are capturable (" + percentage + "%)");
        System.out.println("   Estimated new jobs: ~" + (capturable * 5) + " (assuming 5 jobs/company)");
        System.out.println("=".repeat(70) + "\n");
    }

    /**
     * Count approximate number of job postings in HTML.
     */
    private int countJobs(String html) {
        // Count common job-related patterns
        int count = 0;

        String lowerHtml = html.toLowerCase();

        // Count links with "job" or "position" in href
        count += countOccurrences(lowerHtml, "href=\"/careers/");
        count += countOccurrences(lowerHtml, "href=\"/jobs/");
        count += countOccurrences(lowerHtml, "href=\"/job/");

        // Avoid overcounting (take max)
        return Math.min(count, 50); // Cap at 50 to avoid false positives
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;

        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }

        return count;
    }
}