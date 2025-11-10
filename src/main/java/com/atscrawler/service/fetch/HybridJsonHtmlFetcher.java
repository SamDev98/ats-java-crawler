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
 * Abstract hybrid fetcher that automatically detects whether a response is JSON or HTML.
 *
 * <p>This fetcher allows flexibility when working with different ATS systems that may
 * serve JSON APIs or HTML-based job pages. It automatically parses responses based
 * on their format and delegates parsing logic to abstract methods.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Detects response type (JSON or HTML)</li>
 *   <li>Delegates parsing to {@link #parseJson(String, JsonNode)} or {@link #parseHtml(String, Document)}</li>
 *   <li>Normalizes job metadata (firstSeen, lastSeen, active)</li>
 *   <li>Logs structured output and warnings when no jobs are parsed</li>
 * </ul>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Component
public abstract class HybridJsonHtmlFetcher implements JobFetcher {

    /** Logger for subclass use. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** HTTP utility used to fetch remote data. */
    protected final Http http;

    /** Shared JSON mapper instance. */
    protected final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs a new hybrid fetcher with HTTP dependency injection.
     *
     * @param http HTTP helper used for remote requests
     */
    @Autowired
    protected HybridJsonHtmlFetcher(Http http) {
        this.http = http;
    }

    /** @return list of company slugs or domains */
    protected abstract List<String> getCompanySlugs();

    /** @return the full API or HTML URL for a given company */
    protected abstract String buildUrl(String companySlug);

    /** Parses JSON content into a list of jobs. */
    protected abstract List<Job> parseJson(String company, JsonNode root);

    /** Parses HTML content into a list of jobs. */
    protected abstract List<Job> parseHtml(String company, Document doc);

    /**
     * Fetches and parses job data for all configured companies.
     *
     * <p>Automatically detects content type:
     * <ul>
     *   <li>If body starts with `{` or `[`, treats it as JSON</li>
     *   <li>Otherwise, parses HTML</li>
     * </ul>
     *
     * @return list of normalized {@link Job} objects
     */
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
                if (body.trim().startsWith("{") || body.trim().startsWith("[")) {
                    JsonNode json = mapper.readTree(body);
                    List<Job> jobs = parseJson(company, json);
                    out.addAll(jobs);
                    log.info("✅ {} (JSON) - {} jobs from {}", getSourceName(), jobs.size(), company);
                } else {
                    Document doc = Jsoup.parse(body, url);
                    List<Job> jobs = parseHtml(company, doc);

                    if (jobs.isEmpty()) {
                        log.warn("⚠️ {} (HTML) - ZERO jobs parsed from {} - possible selector change",
                                getSourceName(), company);
                    }

                    out.addAll(jobs);
                    log.info("✅ {} (HTML) - {} jobs from {}", getSourceName(), jobs.size(), company);
                }

                out.forEach(job -> {
                    job.setFirstSeen(LocalDate.now());
                    job.setLastSeen(LocalDate.now());
                    job.setActive(true);
                });

            } catch (Exception e) {
                log.error("❌ {} failed for {} -> {}", getSourceName(), company, e.getMessage());
            }
        }

        return out;
    }
}
