package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.atscrawler.util.Http;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetcher implementation for the Greenhouse ATS platform.
 *
 * <p>Uses the public Greenhouse API to retrieve job listings in JSON format.
 * Converts the JSON response into normalized {@link Job} entities.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Builds company-specific Greenhouse API URLs</li>
 *   <li>Parses job title, URL, and location data</li>
 *   <li>Handles empty or invalid responses safely</li>
 * </ul>
 *
 * <p>Example API endpoint:
 * <pre>{@code https://boards-api.greenhouse.io/v1/boards/{company}/jobs}</pre>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Component
public class GreenhouseFetcher extends AbstractJsonFetcher {

    private final CrawlerProperties props;

    /**
     * Constructs a new {@code GreenhouseFetcher}.
     *
     * @param props crawler configuration containing company slugs
     * @param http  HTTP helper for API requests
     */
    public GreenhouseFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getCompanySlugs() {
        return props.getGreenhouseCompanies();
    }

    /** {@inheritDoc} */
    @Override
    protected String buildUrl(String companySlug) {
        return "https://boards-api.greenhouse.io/v1/boards/" + companySlug + "/jobs";
    }

    /** {@inheritDoc} */
    @Override
    protected List<Job> parseJobs(String company, JsonNode root) {
        List<Job> out = new ArrayList<>();
        JsonNode jobs = root.path("jobs");
        if (!jobs.isArray()) return out;

        for (JsonNode j : jobs) {
            Job job = new Job();
            job.setSource("Greenhouse");
            job.setCompany(company);
            job.setTitle(j.path("title").asText(""));
            job.setUrl(j.path("absolute_url").asText(""));
            job.setNotes(j.path("location").path("name").asText(""));
            out.add(job);
        }
        return out;
    }

    /** {@inheritDoc} */
    @Override
    public String getSourceName() {
        return "Greenhouse";
    }
}
