package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.atscrawler.util.Http;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetcher implementation for the Recruitee ATS platform.
 *
 * <p>Fetches job listings via Recruitee’s public JSON API.
 * Builds company-specific URLs and parses job data fields including title, URL, and locations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Fetch JSON offers for each company</li>
 *   <li>Parse core job attributes and normalize them into {@link Job} entities</li>
 *   <li>Handle missing or malformed responses safely</li>
 * </ul>
 *
 * <p>Example endpoint:
 * <pre>{@code https://{company}.recruitee.com/api/offers/}</pre>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Component
public class RecruiteeFetcher extends AbstractJsonFetcher {
    private final CrawlerProperties props;

    /**
     * Constructs a new {@code RecruiteeFetcher}.
     *
     * @param props crawler configuration with company list
     * @param http  HTTP helper for API requests
     */
    public RecruiteeFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> getCompanySlugs() {
        return props.getRecruiteeCompanies();
    }

    /** {@inheritDoc} */
    @Override
    protected String buildUrl(String companySlug) {
        return "https://" + companySlug + ".recruitee.com/api/offers/";
    }

    /** {@inheritDoc} */
    @Override
    protected List<Job> parseJobs(String company, JsonNode root) {
        List<Job> out = new ArrayList<>();
        JsonNode offers = root.path("offers");
        if (!offers.isArray()) return out;

        for (JsonNode j : offers) {
            Job job = new Job();
            job.setSource("Recruitee");
            job.setCompany(company);
            job.setTitle(j.path("title").asText(""));
            job.setUrl(j.path("careers_url").asText(""));
            job.setNotes(j.path("locations").toString());
            out.add(job);
        }
        return out;
    }

    /** {@inheritDoc} */
    @Override
    public String getSourceName() {
        return "Recruitee";
    }
}
