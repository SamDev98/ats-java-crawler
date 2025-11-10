package com.atscrawler.service;

import com.atscrawler.config.CrawlerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service responsible for loading ATS company data from CSV files.
 * Implements Windows-compatible path resolution with multiple fallback strategies.
 *
 * <p>CSV Format: Company|URL|ATS|Timestamp
 *
 * <p>Search priority:
 * <ol>
 *   <li>Environment variable CRAWLER_ATS_CSV</li>
 *   <li>Application property crawler.ats-csv</li>
 *   <li>Relative to JAR directory</li>
 *   <li>Working directory</li>
 * </ol>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Service
public class ATSCompanyLoader {
    private static final Logger log = LoggerFactory.getLogger(ATSCompanyLoader.class);
    private final ATSSlugValidator validator;
    private final CrawlerProperties crawlerProps;

    @Value("${crawler.ats-csv:}")
    private String csvPathFromConfig;

    /**
     * Constructs a new ATSCompanyLoader with required dependencies.
     *
     * @param crawlerProps the crawler configuration properties
     * @param validator the slug validator for ATS validation
     */
    public ATSCompanyLoader(CrawlerProperties crawlerProps, ATSSlugValidator validator) {
        this.crawlerProps = crawlerProps;
        this.validator = validator;
    }

    /**
     * Loads companies from CSV file on application ready event.
     * Validates slugs and merges with existing configuration.
     *
     * @throws Exception if CSV parsing or file reading fails
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadCompaniesFromCSV() throws Exception {
        String csvPath = findCSVPath();

        if (csvPath == null) {
            log.warn("⚠️  No CSV file found, skipping ATS company import");
            return;
        }

        log.info("📄 Loading ATS companies from: {}", csvPath);

        Map<String, List<String>> atsSlugs = parseCSV(csvPath);

        // Validate slugs before merge
        atsSlugs.forEach((ats, slugs) -> {
            if (List.of("Greenhouse", "Lever", "Workable").contains(ats)) {
                List<String> validSlugs = validator.filterValidSlugs(ats, slugs);
                atsSlugs.put(ats, validSlugs);
            }
        });

        mergeWithConfig(atsSlugs);

        log.info("✅ Successfully imported companies from {} ATS sources", atsSlugs.size());
    }

    /**
     * Searches for CSV file in multiple locations with Windows compatibility.
     *
     * <p>Priority order:
     * <ol>
     *   <li>Environment variable CRAWLER_ATS_CSV</li>
     *   <li>Configuration property crawler.ats-csv</li>
     *   <li>Relative to JAR location (multiple paths)</li>
     *   <li>Working directory</li>
     * </ol>
     *
     * @return absolute path to CSV file, or null if not found
     */
    private String findCSVPath() {
        // Priority 1: Environment variable (set by script)
        String envPath = System.getenv("CRAWLER_ATS_CSV");
        if (envPath != null && new File(envPath).exists()) {
            return envPath;
        }

        // Priority 2: Explicit configuration
        if (csvPathFromConfig != null && !csvPathFromConfig.isBlank()) {
            File configFile = new File(csvPathFromConfig);
            if (configFile.exists()) {
                return configFile.getAbsolutePath();
            }
        }

        // Priority 3: Relative to JAR (same directory)
        try {
            String jarDir = new File(
                    ATSCompanyLoader.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).getParent();

            String[] relativePaths = {
                    "found_ats.csv",                                    // Same JAR directory
                    "../ats-discovery/ats_results/found_ats.csv",      // Dev structure
                    "../../ats-discovery/ats_results/found_ats.csv",   // Inside target/
                    "ats_results/found_ats.csv"                         // Fallback
            };

            for (String relPath : relativePaths) {
                Path candidate = Paths.get(jarDir, relPath).normalize();
                File file = candidate.toFile();

                if (file.exists()) {
                    log.info("📍 Found CSV at: {}", candidate.toAbsolutePath());
                    return file.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            log.warn("⚠️  Error resolving JAR directory: {}", e.getMessage());
        }

        // Priority 4: Working directory
        File workingDirCsv = new File("found_ats.csv");
        if (workingDirCsv.exists()) {
            return workingDirCsv.getAbsolutePath();
        }

        return null;
    }

    /**
     * Parses CSV file and groups companies by ATS type.
     *
     * <p>Expected CSV format: Company|URL|ATS|Timestamp
     *
     * @param path absolute path to CSV file
     * @return map of ATS type to list of slugs
     * @throws Exception if file reading fails
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
     * Extracts ATS slug from URL or normalizes company name as fallback.
     *
     * <p>Supports URL patterns for:
     * <ul>
     *   <li>Greenhouse: boards.greenhouse.io/[slug]</li>
     *   <li>Lever: jobs.lever.co/[slug]</li>
     *   <li>Ashby: jobs.ashbyhq.com/[slug]</li>
     * </ul>
     *
     * @param company company name (fallback)
     * @param url company careers page URL
     * @return extracted or normalized slug
     */
    private String extractSlug(String company, String url) {
        // Try URL patterns first
        if (url.contains("greenhouse.io/")) {
            return url.replaceAll(".*/boards/([^/]+).*", "$1");
        }
        if (url.contains("lever.co/")) {
            return url.replaceAll(".*lever.co/([^/]+).*", "$1");
        }
        if (url.contains("ashbyhq.com/")) {
            return url.replaceAll(".*ashbyhq.com/([^/]+).*", "$1");
        }

        // Fallback: normalize company name
        return company.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .replaceAll("\\s+", "");
    }

    /**
     * Merges CSV data with existing configuration properties.
     * Performs case-insensitive deduplication.
     *
     * @param csvData map of ATS type to slugs from CSV
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
    }

    /**
     * Merges two lists with case-insensitive deduplication.
     *
     * @param existing current list of slugs
     * @param newOnes new slugs to add
     * @return merged list without duplicates
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
}