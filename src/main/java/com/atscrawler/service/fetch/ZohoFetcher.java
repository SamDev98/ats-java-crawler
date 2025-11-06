package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.atscrawler.util.Http;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ZohoFetcher extends HybridJsonHtmlFetcher {

    private final CrawlerProperties props;

    public ZohoFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() {
        return props.getZohoCompanies();
    }

    @Override
    protected String buildUrl(String companySlug) {
        // endpoint público do Zoho Recruit
        return "https://careers.zohorecruit.com/jobs/Careers?company=" + companySlug;
    }

    @Override
    protected List<Job> parseJson(String company, com.fasterxml.jackson.databind.JsonNode root) {
        return new ArrayList<>();
    }

    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();
        Elements cards = doc.select("a.job-opening, a.opening, .career-list-item a");
        for (Element c : cards) {
            String title = c.text();
            String href = c.absUrl("href");
            if (title.isBlank() || href.isBlank()) continue;
            out.add(new Job("ZohoRecruit", company, title, href));
        }
        return out;
    }

    @Override
    public String getSourceName() {
        return "ZohoRecruit";
    }
}
