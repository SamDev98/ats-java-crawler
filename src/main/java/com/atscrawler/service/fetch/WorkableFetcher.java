package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.atscrawler.util.Http;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class WorkableFetcher extends AbstractJsonFetcher {
    private final CrawlerProperties props;

    public WorkableFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() { return props.getWorkableCompanies(); }

    // src/main/java/com/atscrawler/service/fetch/WorkableFetcher.java
    @Override
    protected String buildUrl(String companySlug) {
        // ✅ FIX: Migrar para API v3
        return "https://apply.workable.com/api/v3/accounts/" + companySlug + "/jobs";
    }

    @Override
    protected List<Job> parseJobs(String company, JsonNode root) {
        List<Job> out = new ArrayList<>();

        // ✅ API v3 usa "results" (não "jobs")
        JsonNode results = root.has("results") ? root.get("results") : root.get("jobs");

        if (results == null || !results.isArray()) {
            log.warn("⚠️ Workable - Invalid JSON structure for {}", company);
            return out;
        }

        for (JsonNode j : results) {
            String title = j.path("title").asText("");
            String url = j.path("url").asText("");

            if (title.isBlank() || url.isBlank()) continue;

            Job job = new Job("Workable", company, title, url);

            // Location: v3 usa "location.location_str"
            String location = j.path("location").path("location_str").asText("");
            if (location.isBlank()) {
                location = j.path("location").path("city").asText("");
            }

            job.setNotes(location);
            out.add(job);
        }

        log.info("✅ Workable ({}) returned {} jobs", company, out.size());
        return out;
    }
    @Override
    public String getSourceName() { return "Workable"; }
}
