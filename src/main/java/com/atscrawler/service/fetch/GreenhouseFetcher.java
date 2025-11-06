package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.atscrawler.util.Http;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class GreenhouseFetcher extends AbstractJsonFetcher {

    private final CrawlerProperties props;

    public GreenhouseFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() { return props.getGreenhouseCompanies(); }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://boards-api.greenhouse.io/v1/boards/" + companySlug + "/jobs";
    }

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

    @Override
    public String getSourceName() { return "Greenhouse"; }
}
