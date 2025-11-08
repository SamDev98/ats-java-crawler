package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.atscrawler.util.Http;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Jobvite fetcher with improved HTML parsing.
 * Note: Jobvite heavily uses JavaScript, some jobs may not be visible in static HTML.
 */
@Component
public class JobviteFetcher extends HybridJsonHtmlFetcher {
    private final CrawlerProperties props;

    public JobviteFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() {
        return props.getJobviteCompanies();
    }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://jobs.jobvite.com/" + companySlug + "/rss";
    }

    @Override
    protected List<Job> parseJson(String company, JsonNode root) {
        return new ArrayList<>();
    }

    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();

        if (doc.select("rss, channel, item").size() > 0) {
            // Parse como XML
            Document xmlDoc = Jsoup.parse(doc.html(), "", org.jsoup.parser.Parser.xmlParser());

            xmlDoc.select("item").forEach(item -> {
                String title = item.select("title").text();
                String link = item.select("link").text();
                String desc = item.select("description").text();

                if (!title.isBlank() && !link.isBlank()) {
                    Job j = new Job("Jobvite", company, title, link);
                    j.setNotes(desc);
                    out.add(j);
                }
            });

            return out;
        }

        // Fallback: HTML parsing (código atual)
        Elements jobs = doc.select(
                "#jv-careersite-listings a, " +
                        ".jv-job-list-item a, " +
                        "a[href*='/job/']"
        );

        for (Element e : jobs) {
            String href = e.absUrl("href");

            // Must contain /job/ and jobvite.com
            if (href.isBlank() || !href.contains("/job/") || !href.contains("jobvite.com")) {
                continue;
            }

            String title = e.select(".jv-job-list-name, h3, h4").text();
            if (title.isBlank()) {
                title = e.text().trim();
            }

            if (title.isBlank() || title.length() > 200) continue;

            // Extract location
            String location = e.select(".jv-job-list-location").text();

            Job j = new Job("Jobvite", company, title, href);
            if (!location.isBlank()) {
                j.setNotes(location);
            }

            out.add(j);
        }

        return out;
    }

    @Override
    public String getSourceName() {
        return "Jobvite";
    }
}