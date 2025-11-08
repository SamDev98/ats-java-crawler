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

    @Override
    protected String buildUrl(String companySlug) {
        return "https://apply.workable.com/api/v1/accounts/" + companySlug + "/jobs";
    }

    @Override
    protected List<Job> parseJobs(String company, JsonNode root) {
        List<Job> out = new ArrayList<>();
        JsonNode jobs = root.path("results");
        if (!jobs.isArray()) return out;

        for (JsonNode j : jobs) {
            Job job = new Job();
            job.setSource("Workable");
            job.setCompany(company);
            job.setTitle(j.path("title").asText(""));
            job.setUrl(j.path("url").asText(""));
            job.setNotes(j.path("location").path("location_str").asText(""));
            out.add(job);
        }
        return out;
    }

    @Override
    public String getSourceName() { return "Workable"; }
}
