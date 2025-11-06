package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.atscrawler.util.Http;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RipplingFetcher extends HybridJsonHtmlFetcher {

    private final CrawlerProperties props;

    public RipplingFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() { return props.getRipplingCompanies(); }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://" + companySlug + ".rippling-ats.com/jobs";
    }

    @Override
    protected List<Job> parseJson(String company, JsonNode root) { return new ArrayList<>(); }

    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();
        Elements jobs = doc.select("a.job-card");
        for (Element e : jobs) {
            String title = e.select(".job-title").text();
            String href = e.absUrl("href");
            if (title.isBlank()) continue;
            Job j = new Job("Rippling", company, title, href);
            out.add(j);
        }
        return out;
    }

    @Override
    public String getSourceName() { return "Rippling"; }
}
