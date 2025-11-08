package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.atscrawler.util.Http;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LeverFetcher extends AbstractJsonFetcher {

    private final CrawlerProperties props;

    public LeverFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() {
        return props.getLeverCompanies();
    }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://api.lever.co/v0/postings/" + companySlug + "?mode=json";
    }

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

    @Override
    public String getSourceName() {
        return "Lever";
    }
}
