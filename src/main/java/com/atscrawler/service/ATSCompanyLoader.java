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
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ✅ WINDOWS-COMPATIBLE: Loads companies from CSV using relative paths
 */
@Service
public class ATSCompanyLoader {
    private static final Logger log = LoggerFactory.getLogger(ATSCompanyLoader.class);
    private final Http http;
    private final ATSSlugValidator validator;
    private final CrawlerProperties crawlerProps;

    // ✅ FIX: Ordem de prioridade para localizar CSV
    @Value("${crawler.ats-csv:}")
    private String csvPathFromConfig;

    public ATSCompanyLoader(CrawlerProperties crawlerProps, Http http, ATSSlugValidator validator) {
        this.crawlerProps = crawlerProps;
        this.http = http;
        this.validator = validator;
    }

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
     * ✅ FIX: Busca CSV em múltiplos locais (Windows-compatible)
     */
    private String findCSVPath() {
        // Prioridade 1: Variável de ambiente (set pelo script)
        String envPath = System.getenv("CRAWLER_ATS_CSV");
        if (envPath != null && new File(envPath).exists()) {
            return envPath;
        }

        // Prioridade 2: Configuração explícita
        if (csvPathFromConfig != null && !csvPathFromConfig.isBlank()) {
            File configFile = new File(csvPathFromConfig);
            if (configFile.exists()) {
                return configFile.getAbsolutePath();
            }
        }

        // Prioridade 3: Relativo ao JAR (mesmo diretório)
        try {
            String jarDir = new File(
                    ATSCompanyLoader.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).getParent();

            String[] relativePaths = {
                    "found_ats.csv",                                    // Mesmo dir do JAR
                    "../ats-discovery/ats_results/found_ats.csv",      // Estrutura dev/
                    "../../ats-discovery/ats_results/found_ats.csv",   // Dentro de target/
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

        // Prioridade 4: Working directory
        File workingDirCsv = new File("found_ats.csv");
        if (workingDirCsv.exists()) {
            return workingDirCsv.getAbsolutePath();
        }

        return null;
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
    }

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