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
 * BambooHR fetcher with robust selectors.
 * Note: BambooHR is primarily an HRIS, not all companies use the ATS module.
 */
@Component
public class BamboohrFetcher extends HybridJsonHtmlFetcher {
    private final CrawlerProperties props;

    public BamboohrFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() {
        return props.getBamboohrCompanies();
    }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://" + companySlug + ".bamboohr.com/careers/";
    }

    @Override
    protected List<Job> parseJson(String company, JsonNode root) {
        return new ArrayList<>();
    }

    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();

        // Multiple selectors for different BambooHR layouts
        Elements items = doc.select(
                ".BambooHR-ATS-Job-List a, " +
                        ".BambooHR-ATS-Jobs-Item a, " +
                        "a[href*='/careers/'], " +
                        ".opening a, " +
                        ".job-listing a, " +
                        "[class*='job'] a[href*='/job/']"
        );

        for (Element e : items) {
            String href = e.absUrl("href");

            // Validate URL contains bamboohr
            if (href.isBlank() || !href.contains("bamboohr.com")) continue;

            String title = e.text().trim();
            if (title.isBlank() || title.length() > 200) continue;

            // Skip navigation links
            if (title.equalsIgnoreCase("careers") ||
                    title.equalsIgnoreCase("home") ||
                    title.equalsIgnoreCase("back")) {
                continue;
            }

            Job j = new Job("BambooHR", company, title, href);
            out.add(j);
        }

        return out;
    }

    @Override
    public String getSourceName() {
        return "BambooHR";
    }
}