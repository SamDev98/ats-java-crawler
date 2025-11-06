package com.atscrawler.service.fetch;

import com.atscrawler.model.Job;
import com.atscrawler.util.Http;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for JSON-based ATS fetchers.
 * Handles JSON and logs gracefully when the endpoint returns HTML instead.
 */
@Component
public abstract class AbstractJsonFetcher implements JobFetcher {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final Http http;
    protected final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    protected AbstractJsonFetcher(Http http) {
        this.http = http;
    }

    protected abstract List<String> getCompanySlugs();
    protected abstract String buildUrl(String companySlug);
    protected abstract List<Job> parseJobs(String company, JsonNode root) throws Exception;

    @Override
    public List<Job> fetch() {
        List<Job> results = new ArrayList<>();
        List<String> companies = getCompanySlugs();

        if (companies == null || companies.isEmpty()) {
            log.warn("⚠️ No company slugs configured for {}", getSourceName());
            return results;
        }

        for (String company : companies) {
            String url = buildUrl(company);
            String body = http.get(url);

            if (body == null || body.isBlank()) {
                log.warn("⚠️ Empty response from {}", url);
                continue;
            }

            try {
                JsonNode root = mapper.readTree(body);
                List<Job> jobs = parseJobs(company, root);

                for (Job job : jobs) {
                    job.setFirstSeen(LocalDate.now());
                    job.setLastSeen(LocalDate.now());
                    job.setActive(true);
                    results.add(job);
                }

                log.info("✅ {} ({}) returned {} jobs", getSourceName(), company, jobs.size());
            }
            catch (JsonProcessingException e) {
                // HTML response or unexpected non-JSON content
                log.debug("💡 {} ({}) returned HTML instead of JSON — skipping parsing", getSourceName(), company);
            }
            catch (Exception e) {
                log.error("❌ {} failed for {} -> {}", getSourceName(), company, e.getMessage());
            }
        }

        return results;
    }
}
