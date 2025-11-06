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
public class BreezyFetcher extends HybridJsonHtmlFetcher {

    private final CrawlerProperties props;

    public BreezyFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() { return props.getBreezyCompanies(); }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://" + companySlug + ".breezy.hr/";
    }

    @Override
    protected List<Job> parseJson(String company, JsonNode root) { return new ArrayList<>(); }

    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();
        Elements cards = doc.select("a.position, a.position.transition");
        for (Element e : cards) {
            String title = e.select("h2, .position-title").text();
            String url = e.absUrl("href");
            if (!title.isBlank() && !url.isBlank()) {
                out.add(new Job("BreezyHR", company, title, url));
            }
        }
        return out;
    }

    @Override
    public String getSourceName() { return "BreezyHR"; }
}
