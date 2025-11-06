package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.atscrawler.util.Http;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class IsmartFetcher extends AbstractJsonFetcher {
    private final CrawlerProperties props;

    public IsmartFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() { return props.getIsmartCompanies(); }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://" + companySlug + ".ismartrecruit.com/api/jobs";
    }

    @Override
    protected List<Job> parseJobs(String company, JsonNode root) { return new ArrayList<>(); }

    @Override
    public String getSourceName() { return "iSmartRecruit"; }
}
