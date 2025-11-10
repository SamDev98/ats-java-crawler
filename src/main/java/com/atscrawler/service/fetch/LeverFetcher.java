package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.atscrawler.util.Http;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetcher implementation for the Lever ATS platform.
 *
 * <p>Consumes Lever’s public job postings API and transforms
 * JSON data into normalized {@link Job} entities.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Builds Lever API endpoints for each company</li>
 *   <li>Parses job title, URL, and location data</li>
 *   <li>Handles blank or invalid entries safely</li>
 * </ul>
 *
 * <p>Example API endpoint:
 * <pre>{@code https://api.lever.co/v0/postings/{company}?mode=json}</pre>
 *
 * @see AbstractJsonFetcher
 * @author SamDev98
 * @since 0.4.1
 */
@Component
public class LeverFetcher extends AbstractJsonFetcher {

    private final CrawlerProperties props;

    /**
     * Constructs a new {@code LeverFetcher}.
     *
     * @param props crawler configuration containing company slugs
     * @param http  HTTP helper for API requests
     */
    public LeverFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getCompanySlugs() {
        return props.getLeverCompanies();
    }

    /** {@inheritDoc} */
    @Override
    protected String buildUrl(String companySlug) {
        return "https://api.lever.co/v0/postings/" + companySlug + "?mode=json";
    }

    /** {@inheritDoc} */
    @Override
    protected List<Job> parseJobs(String company, JsonNode root) {
        List<Job> out = new ArrayList<>();
        if (root == null || !root.isArray()) return out;

        for (JsonNode j : root) {
            String title = j.path("text").asText("");
            String url = j.path("hostedUrl").asText("");
            if (title.isBlank() || url.isBlank()) continue;

            Job job = new Job();
            job.setSource("Lever");
            job.setCompany(company);
            job.setTitle(title);
            job.setUrl(url);
            job.setNotes(j.path("categories").path("location").asText(""));
            out.add(job);
        }

        return out;
    }

    /** {@inheritDoc} */
    @Override
    public String getSourceName() {
        return "Lever";
    }
}
