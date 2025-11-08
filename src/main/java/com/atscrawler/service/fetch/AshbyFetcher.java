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
 * Ashby fetcher - uses HTML parsing since Ashby does not provide public JSON API.
 * Page structure: <a href="https://jobs.ashbyhq.com/">...</a>{company}
 */
@Component
public class AshbyFetcher extends HybridJsonHtmlFetcher {
    private final CrawlerProperties props;

    public AshbyFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() {
        return props.getAshbyCompanies();
    }

    @Override
    protected String buildUrl(String companySlug) {
        return "https://jobs.ashbyhq.com/" + companySlug;
    }

    @Override
    protected List<Job> parseJson(String company, JsonNode root) {
        // Ashby doesn't provide public JSON API
        return new ArrayList<>();
    }

    // src/main/java/com/atscrawler/service/fetch/AshbyFetcher.java
    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();

        // ✅ SELETORES ATUALIZADOS (2024) - múltiplos fallbacks
        Elements jobs = doc.select(
                "a[href*='/applications/'], " +      // Padrão 2024
                        "a[href*='/jobs/'], " +               // Alternativo
                        "div[class*='JobsList'] a, " +        // Grid container
                        "div[class*='ashby-job'] a, " +       // Legacy
                        "a[class*='JobPosting'] a, " +        // CSS Modules
                        ".job-link, [data-job-id]"            // Fallback genérico
        );

        for (Element e : jobs) {
            String href = e.absUrl("href");
            if (href.isBlank()) {
                href = "https://jobs.ashbyhq.com" + e.attr("href");
            }

            // Validar URL contém ashbyhq
            if (!href.contains("ashbyhq.com") || href.isBlank()) continue;

            String title = e.select(".ashby-job-posting-title, [class*='Title']").text();
            if (title.isBlank()) {
                title = e.text().trim();
            }

            if (title.isBlank() || title.length() > 200) continue;

            Job j = new Job("Ashby", company, title, href);

            String location = e.select(".ashby-job-posting-location, [class*='Location']").text();
            if (!location.isBlank()) {
                j.setNotes(location);
            }

            out.add(j);
        }

        return out;
    }
    @Override
    public String getSourceName() {
        return "Ashby";
    }
}