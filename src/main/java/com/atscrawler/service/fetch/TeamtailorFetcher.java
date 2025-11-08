package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.atscrawler.util.Http;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Teamtailor fetcher - uses public JSON API.
 * API docs: https://docs.teamtailor.com/#jobs
 * No authentication required for public job listings.
 */
@Component
public class TeamtailorFetcher extends AbstractJsonFetcher {
    private final CrawlerProperties props;

    public TeamtailorFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() {
        return props.getTeamtailorCompanies();
    }

    @Override
    protected String buildUrl(String companySlug) {
        // ✅ FIX: Detectar se é domínio completo ou slug simples
        if (companySlug.contains(".teamtailor.com")) {
            // Formato: loft.teamtailor.com
            return "https://" + companySlug + "/api/v1/jobs";
        }

        // Fallback: Slug simples (pode não funcionar)
        return "https://career.teamtailor.com/api/v1/jobs?company=" + companySlug;
    }

    @Override
    protected List<Job> parseJobs(String company, JsonNode root) {
        List<Job> out = new ArrayList<>();
        JsonNode data = root.path("data");

        if (!data.isArray()) {
            log.warn("⚠️ Teamtailor ({}) - unexpected JSON structure", company);
            return out;
        }

        for (JsonNode j : data) {
            JsonNode attrs = j.path("attributes");
            JsonNode links = j.path("links");

            String title = attrs.path("title").asText("");
            String url = links.path("careersite-job-url").asText("");

            if (title.isBlank() || url.isBlank()) continue;

            Job job = new Job("Teamtailor", company, title, url);

            // Extract location
            String location = attrs.path("location").asText("");
            if (!location.isBlank()) {
                job.setNotes(location);
            }

            out.add(job);
        }

        return out;
    }

    @Override
    public String getSourceName() {
        return "Teamtailor";
    }
}