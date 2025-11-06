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

/**
 * BreezyHR fetcher with robust CSS selectors.
 * Multiple fallback selectors to handle different page structures.
 */
@Component
public class BreezyFetcher extends HybridJsonHtmlFetcher {
    private final CrawlerProperties props;

    public BreezyFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() {
        return props.getBreezyCompanies();
    }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://" + companySlug + ".breezy.hr/";
    }

    @Override
    protected List<Job> parseJson(String company, JsonNode root) {
        return new ArrayList<>();
    }

    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();

        // Multiple selectors for better coverage
        Elements cards = doc.select(
                "a.position, " +
                        "a.position.transition, " +
                        "a[href*='/p/'], " +
                        "a[href*='/position/'], " +
                        ".position-card a, " +
                        ".job-listing a"
        );

        for (Element e : cards) {
            String url = e.absUrl("href");

            // Validate URL
            if (url.isBlank() || !url.contains("breezy.hr")) continue;

            // Try multiple title selectors
            String title = e.select("h2, .position-title, .job-title").text();
            if (title.isBlank()) {
                title = e.text().trim();
            }

            if (title.isBlank() || title.length() > 200) continue;

            // Extract location if available
            String location = e.select(".location, .job-location").text();

            Job j = new Job("BreezyHR", company, title, url);
            if (!location.isBlank()) {
                j.setNotes(location);
            }

            out.add(j);
        }

        return out;
    }

    @Override
    public String getSourceName() {
        return "BreezyHR";
    }
}