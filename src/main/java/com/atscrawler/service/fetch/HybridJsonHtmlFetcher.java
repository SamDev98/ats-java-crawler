package com.atscrawler.service.fetch;

import com.atscrawler.model.Job;
import com.atscrawler.util.Http;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Hybrid fetcher: automatically detects JSON or HTML response.
 * Allows flexible parsing of different ATS endpoints.
 */
@Component
public abstract class HybridJsonHtmlFetcher implements JobFetcher {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final Http http;
    protected final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    protected HybridJsonHtmlFetcher(Http http) {
        this.http = http;
    }

    protected abstract List<String> getCompanySlugs();
    protected abstract String buildUrl(String companySlug);
    protected abstract List<Job> parseJson(String company, JsonNode root);
    protected abstract List<Job> parseHtml(String company, Document doc);

    @Override
    public List<Job> fetch() {
        List<Job> out = new ArrayList<>();
        List<String> companies = getCompanySlugs();
        if (companies == null || companies.isEmpty()) return out;

        for (String company : companies) {
            String url = buildUrl(company);
            String body = http.get(url);

            if (body == null || body.isBlank()) {
                log.warn("⚠️ Empty response from {}", url);
                continue;
            }

            try {
                // Detect JSON or HTML automatically
                if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
                    JsonNode json = mapper.readTree(body);
                    out.addAll(parseJson(company, json));
                    log.info("✅ {} (JSON) parsed successfully for {}", getSourceName(), company);
                } else {
                    Document doc = Jsoup.parse(body, url);
                    out.addAll(parseHtml(company, doc));
                    log.info("✅ {} (HTML) parsed successfully for {}", getSourceName(), company);
                }

                out.forEach(j -> {
                    j.setFirstSeen(LocalDate.now());
                    j.setLastSeen(LocalDate.now());
                    j.setActive(true);
                });

            } catch (Exception e) {
                log.error("❌ {} failed for {} -> {}", getSourceName(), company, e.getMessage());
            }
        }

        return out;
    }
}
