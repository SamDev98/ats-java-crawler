package com.atscrawler.service;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.util.Http;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads companies from ATS discovery CSV and populates crawler config.
 * Runs BEFORE first sync to ensure all companies are configured.
 */
@Service
public class ATSCompanyLoader {
    private static final Logger log = LoggerFactory.getLogger(ATSCompanyLoader.class);
    private final Http http;

    @Value("${crawler.ats-csv:C:/Users/SammyJr/dev/ats_discover/ats_results/found_ats.csv}")
    private String csvPath;

    private final CrawlerProperties crawlerProps;

    public ATSCompanyLoader(CrawlerProperties crawlerProps, Http http) {
        this.crawlerProps = crawlerProps;
        this.http = http;
    }

    /**
     * Loads CSV on application startup, BEFORE first sync.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadCompaniesFromCSV() {
        log.info("📋 Loading companies from ATS CSV: {}", csvPath);

        try {
            Map<String, List<String>> atsSlugs = parseCSV(csvPath);

            // Merge with existing config (CSV takes precedence)
            mergeWithConfig(atsSlugs);

            log.info("✅ Loaded {} ATSs from CSV", atsSlugs.size());
            atsSlugs.forEach((ats, companies) ->
                    log.info("   {} → {} companies", ats, companies.size())
            );

        } catch (Exception e) {
            log.warn("⚠️ Could not load ATS CSV (using config only): {}", e.getMessage());
        }
    }

    /**
     * Parse CSV format: Company|URL|ATS|Timestamp
     */
    private Map<String, List<String>> parseCSV(String path) throws Exception {
        Map<String, List<String>> result = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            reader.readLine(); // Skip header

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length < 3) continue;

                String company = parts[0].trim();
                String url = parts[1].trim();
                String ats = parts[2].trim();

                String slug = extractSlug(company, url);

                result.computeIfAbsent(ats, k -> new ArrayList<>()).add(slug);
            }
        }

        return result;
    }

    /**
     * Extract ATS slug from company name or URL.
     */
    private String extractSlug(String company, String url) {
        // Try URL patterns first
        if (url.contains("greenhouse.io/")) {
            return url.replaceAll(".*/boards/([^/]+).*", "$1");
        }
        if (url.contains("lever.co/")) {
            return url.replaceAll(".*/jobs/lever.co/([^/]+).*", "$1");
        }
        if (url.contains("ashbyhq.com/")) {
            return url.replaceAll(".*/ashbyhq.com/([^/]+).*", "$1");
        }

        // Fallback: normalize company name
        return company.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .replaceAll("\\s+", "");
    }

    /**
     * Merge CSV data with application.properties config.
     * CSV entries are ADDED to existing config.
     */
    private void mergeWithConfig(Map<String, List<String>> csvData) {
        // Greenhouse
        if (csvData.containsKey("Greenhouse")) {
            List<String> existing = crawlerProps.getGreenhouseCompanies();
            List<String> merged = merge(existing, csvData.get("Greenhouse"));
            crawlerProps.setGreenhouseCompanies(merged);
        }

        // Lever
        if (csvData.containsKey("Lever")) {
            List<String> existing = crawlerProps.getLeverCompanies();
            List<String> merged = merge(existing, csvData.get("Lever"));
            crawlerProps.setLeverCompanies(merged);
        }

        // Workable
        if (csvData.containsKey("Workable")) {
            List<String> existing = crawlerProps.getWorkableCompanies();
            List<String> merged = merge(existing, csvData.get("Workable"));
            crawlerProps.setWorkableCompanies(merged);
        }

        // Ashby
        if (csvData.containsKey("Ashby")) {
            List<String> existing = crawlerProps.getAshbyCompanies();
            List<String> merged = merge(existing, csvData.get("Ashby"));
            crawlerProps.setAshbyCompanies(merged);
        }

        // BreezyHR
        if (csvData.containsKey("BreezyHR")) {
            List<String> existing = crawlerProps.getBreezyCompanies();
            List<String> merged = merge(existing, csvData.get("BreezyHR"));
            crawlerProps.setBreezyCompanies(merged);
        }

        // Recruitee
        if (csvData.containsKey("Recruitee")) {
            List<String> existing = crawlerProps.getRecruiteeCompanies();
            List<String> merged = merge(existing, csvData.get("Recruitee"));
            crawlerProps.setRecruiteeCompanies(merged);
        }

        // Teamtailor
        if (csvData.containsKey("Teamtailor")) {
            List<String> existing = crawlerProps.getTeamtailorCompanies();
            List<String> merged = merge(existing, csvData.get("Teamtailor"));
            crawlerProps.setTeamtailorCompanies(merged);
        }

        // Jobvite
        if (csvData.containsKey("Jobvite")) {
            List<String> existing = crawlerProps.getJobviteCompanies();
            List<String> merged = merge(existing, csvData.get("Jobvite"));
            crawlerProps.setJobviteCompanies(merged);
        }

        // BambooHR (skip - não vale a pena, 44 companies mas 0 jobs)
    }

    /**
     * Merge two lists, removing duplicates (case-insensitive).
     */
    private List<String> merge(List<String> existing, List<String> newOnes) {
        Set<String> normalized = existing.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<String> result = new ArrayList<>(existing);

        for (String company : newOnes) {
            if (!normalized.contains(company.toLowerCase())) {
                result.add(company);
                normalized.add(company.toLowerCase());
            }
        }

        return result;
    }

    private String detectRealATS(String company, String url) {
        // Para BambooHR, tentar detectar o ATS real
        if (url.contains("bamboohr.com")) {
            String html = http.get(url);

            if (html != null) {
                if (html.contains("greenhouse.io")) return "Greenhouse";
                if (html.contains("lever.co")) return "Lever";
                if (html.contains("ashbyhq.com")) return "Ashby";
                if (html.contains("workable.com")) return "Workable";
            }
        }

        return "BambooHR"; // Fallback
    }
}