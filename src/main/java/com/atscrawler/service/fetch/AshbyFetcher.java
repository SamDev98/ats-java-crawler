package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.atscrawler.util.Http;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class AshbyFetcher extends AbstractJsonFetcher {
    private final CrawlerProperties props;

    public AshbyFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() { return props.getAshbyCompanies(); }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://jobs.ashbyhq.com/api/posting/" + companySlug;
    }

    @Override
    protected List<Job> parseJobs(String company, JsonNode root) {
        List<Job> out = new ArrayList<>();
        if (!root.isArray()) return out;

        for (JsonNode j : root) {
            Job job = new Job();
            job.setSource("Ashby");
            job.setCompany(company);
            job.setTitle(j.path("title").asText(""));
            job.setUrl(j.path("jobUrl").asText(""));
            job.setNotes(j.path("location").asText(""));
            out.add(job);
        }
        return out;
    }

    @Override
    public String getSourceName() { return "Ashby"; }
}
