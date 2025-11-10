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
 * Abstract base class for JSON-based job fetchers.
 *
 * <p>This class provides shared logic for fetchers that consume JSON APIs from ATS (Applicant Tracking Systems).
 * It handles HTTP GET requests, JSON parsing, and job normalization.
 * If an endpoint returns HTML instead of JSON, it logs the issue gracefully.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Builds company-specific URLs</li>
 *   <li>Fetches and parses JSON data</li>
 *   <li>Handles common error cases (empty response, HTML fallback, etc.)</li>
 *   <li>Normalizes job timestamps and activation flags</li>
 * </ul>
 *
 * <p>Implementations must define:
 * <ul>
 *   <li>{@link #getCompanySlugs()}</li>
 *   <li>{@link #buildUrl(String)}</li>
 *   <li>{@link #parseJobs(String, JsonNode)}</li>
 * </ul>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Component
public abstract class AbstractJsonFetcher implements JobFetcher {

    /** Logger instance for subclass-specific logging. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** HTTP utility for GET requests. */
    protected final Http http;

    /** Shared JSON parser. */
    protected final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs a JSON fetcher with an injected HTTP utility.
     *
     * @param http the HTTP helper used for performing network requests
     */
    @Autowired
    protected AbstractJsonFetcher(Http http) {
        this.http = http;
    }

    /**
     * Returns a list of company slugs or identifiers to be fetched.
     *
     * @return list of company slugs
     */
    protected abstract List<String> getCompanySlugs();

    /**
     * Builds the request URL for a specific company.
     *
     * @param companySlug the company slug or domain part
     * @return full request URL
     */
    protected abstract String buildUrl(String companySlug);

    /**
     * Parses the JSON response and converts it to a list of {@link Job} objects.
     *
     * @param company company slug or name
     * @param root    parsed JSON root node
     * @return list of parsed jobs
     * @throws Exception if parsing fails
     */
    protected abstract List<Job> parseJobs(String company, JsonNode root) throws Exception;

    /**
     * Fetches job data from all configured company sources.
     *
     * <p>This method iterates through all company slugs, fetches JSON content,
     * parses it using {@link #parseJobs(String, JsonNode)}, and normalizes each
     * job’s metadata (dates and activation status).
     *
     * @return list of fetched and normalized jobs
     */
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
            } catch (JsonProcessingException e) {
                log.debug("💡 {} ({}) returned HTML instead of JSON — skipping parsing",
                        getSourceName(), company);
            } catch (Exception e) {
                log.error("❌ {} failed for {} -> {}", getSourceName(), company, e.getMessage());
            }
        }

        return results;
    }
}
