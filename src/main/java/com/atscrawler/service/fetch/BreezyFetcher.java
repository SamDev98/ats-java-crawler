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
 * Fetcher for BreezyHR job listings.
 *
 * <p>Uses HTML scraping with multiple CSS selector fallbacks to handle different
 * layout variations across company pages. JSON parsing is stubbed for potential
 * future API integration.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Loads company list from {@link CrawlerProperties}</li>
 *   <li>Fetches HTML from BreezyHR career pages</li>
 *   <li>Parses job titles, URLs, and optional locations</li>
 * </ul>
 *
 * <p>Example URL format:
 * <pre>{@code https://companyslug.breezy.hr/}</pre>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Component
public class BreezyFetcher extends HybridJsonHtmlFetcher {
    private final CrawlerProperties props;

    /**
     * Constructs a new BreezyHR fetcher with injected configuration and HTTP client.
     *
     * @param props configuration properties containing company slugs
     * @param http  HTTP helper for network access
     */
    public BreezyFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    /**
     * Returns the configured BreezyHR company slugs.
     *
     * @return list of company identifiers
     */
    @Override
    protected List<String> getCompanySlugs() {
        return props.getBreezyCompanies();
    }

    /**
     * Builds the BreezyHR URL for a specific company.
     *
     * @param companySlug the company slug
     * @return full company career page URL
     */
    @Override
    protected String buildUrl(String companySlug) {
        return "https://" + companySlug + ".breezy.hr/";
    }

    /**
     * Parses the JSON response (not currently used for BreezyHR).
     *
     * @param company company name
     * @param root    JSON root node
     * @return empty list, since BreezyHR currently uses HTML parsing
     */
    @Override
    protected List<Job> parseJson(String company, JsonNode root) {
        return new ArrayList<>();
    }

    /**
     * Parses HTML content to extract job listings.
     *
     * <p>Uses multiple fallback CSS selectors to adapt to changes in BreezyHR’s
     * front-end structure. Extracts URLs, titles, and optional locations.
     *
     * @param company company name or slug
     * @param doc     parsed HTML document
     * @return list of parsed {@link Job} objects
     */
    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();

        // Multiple selectors for flexible matching
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

            // Validate URL format
            if (url.isBlank() || !url.contains("breezy.hr")) continue;

            // Extract title using fallback selectors
            String title = e.select("h2, .position-title, .job-title").text();
            if (title.isBlank()) {
                title = e.text().trim();
            }

            if (title.isBlank() || title.length() > 200) continue;

            // Extract optional location information
            String location = e.select(".location, .job-location").text();

            Job job = new Job("BreezyHR", company, title, url);
            if (!location.isBlank()) {
                job.setNotes(location);
            }

            out.add(job);
        }

        return out;
    }

    /**
     * Returns the human-readable source name for this fetcher.
     *
     * @return constant string {@code "BreezyHR"}
     */
    @Override
    public String getSourceName() {
        return "BreezyHR";
    }
}
