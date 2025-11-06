package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.atscrawler.util.Http;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecruiteeFetcher extends AbstractJsonFetcher {
    private final CrawlerProperties props;

    public RecruiteeFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() { return props.getRecruiteeCompanies(); }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://" + companySlug + ".recruitee.com/api/offers/";
    }

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

    @Override
    public String getSourceName() { return "Recruitee"; }
}
