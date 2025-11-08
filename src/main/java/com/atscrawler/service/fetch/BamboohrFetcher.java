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
 * ✅ IMPROVED BambooHR Fetcher - 3-tier strategy:
 * 1. Detect redirects to other ATS (Greenhouse, Lever, Ashby)
 * 2. Parse BambooHR native pages (when active)
 * 3. Try alternative career page patterns
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
        // Try BambooHR first
        return "https://" + companySlug + ".bamboohr.com/careers/";
    }

    @Override
    protected List<Job> parseJson(String company, JsonNode root) {
        return new ArrayList<>();
    }

    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();

        // ✅ STRATEGY 1: Detect redirect to other ATS
        String redirectedATS = detectRedirect(doc);
        if (redirectedATS != null) {
            log.info("🔀 {} redirects to {} (skipping - will be fetched by {})",
                    company, redirectedATS, redirectedATS);
            return out; // Empty - será capturado pelo fetcher correto
        }

        // ✅ STRATEGY 2: Parse BambooHR native page
        out.addAll(parseBambooHRNative(company, doc));

        // ✅ STRATEGY 3: Try alternative patterns
        if (out.isEmpty()) {
            out.addAll(parseAlternativePatterns(company, doc));
        }

        return out;
    }

    /**
     * Detect if page redirects to another ATS.
     */
    private String detectRedirect(Document doc) {
        String html = doc.html();

        if (html.contains("greenhouse.io/embed/job_board") ||
                html.contains("boards.greenhouse.io")) {
            return "Greenhouse";
        }

        if (html.contains("jobs.lever.co") ||
                html.contains("lever.co/embed")) {
            return "Lever";
        }

        if (html.contains("jobs.ashbyhq.com")) {
            return "Ashby";
        }

        if (html.contains("apply.workable.com")) {
            return "Workable";
        }

        return null;
    }

    /**
     * Parse BambooHR native career pages.
     */
    private List<Job> parseBambooHRNative(String company, Document doc) {
        List<Job> out = new ArrayList<>();

        // ✅ MULTIPLE SELECTORS for different BambooHR layouts
        Elements items = doc.select(
                ".BambooHR-ATS-Job-List a, " +              // Official class
                        ".BambooHR-ATS-Jobs-Item a, " +             // Item wrapper
                        "a[href*='/careers/'], " +                   // Career links
                        ".opening a, " +                             // Generic opening
                        ".job-listing a, " +                         // Job listing
                        "[class*='job'] a[href*='/job/'], " +       // Contains 'job' in class
                        "li[class*='job'] a, " +                     // List items
                        "div[class*='position'] a, " +               // Position wrapper
                        "table.jobs a, " +                           // Table layout
                        "ul.careers a"                               // List layout
        );

        for (Element e : items) {
            String href = e.absUrl("href");

            // ✅ VALIDATION: Must contain bamboohr or be relative
            if (href.isBlank()) continue;

            boolean isBambooHR = href.contains("bamboohr.com");
            boolean isRelative = e.attr("href").startsWith("/");

            if (!isBambooHR && !isRelative) continue;

            // ✅ Fix relative URLs
            if (isRelative && !href.contains("http")) {
                href = "https://" + company + ".bamboohr.com" + e.attr("href");
            }

            String title = e.text().trim();

            // ✅ FILTER: Skip navigation/header links
            if (title.isBlank() || title.length() > 200) continue;
            if (isNavigationLink(title)) continue;

            Job j = new Job("BambooHR", company, title, href);

            // Extract location from parent elements
            String location = extractLocation(e);
            if (!location.isBlank()) {
                j.setNotes(location);
            }

            out.add(j);
        }

        return out;
    }

    /**
     * Try alternative career page patterns (custom domains).
     */
    private List<Job> parseAlternativePatterns(String company, Document doc) {
        List<Job> out = new ArrayList<>();

        // ✅ Some companies use custom domains like:
        // - careers.company.com
        // - jobs.company.com
        // - company.com/careers

        // Try to detect these patterns
        Elements allLinks = doc.select("a[href]");

        for (Element link : allLinks) {
            String href = link.absUrl("href");
            String text = link.text().toLowerCase();

            // Check if link points to external career page
            if ((href.contains("careers") || href.contains("jobs")) &&
                    (text.contains("career") || text.contains("job") ||
                            text.contains("position") || text.contains("opening"))) {

                // This might be a redirect to external career page
                log.debug("🔍 {} - Found potential career link: {}", company, href);

                // Try to fetch that page
                try {
                    String externalHtml = http.get(href);
                    if (externalHtml != null && externalHtml.contains("job")) {
                        Document externalDoc = org.jsoup.Jsoup.parse(externalHtml, href);

                        // Check if it's another ATS
                        String externalATS = detectRedirect(externalDoc);
                        if (externalATS != null) {
                            log.info("🔀 {} uses external ATS: {} at {}",
                                    company, externalATS, href);
                        }
                    }
                } catch (Exception e) {
                    // Ignore errors from external pages
                }
            }
        }

        return out;
    }

    /**
     * Extract location from nearby elements.
     */
    private String extractLocation(Element jobLink) {
        // Try parent elements
        Element parent = jobLink.parent();
        if (parent != null) {
            String locationText = parent.select(
                    ".location, .job-location, [class*='location']"
            ).text();

            if (!locationText.isBlank()) {
                return locationText;
            }
        }

        // Try sibling elements
        Element sibling = jobLink.nextElementSibling();
        if (sibling != null &&
                (sibling.className().contains("location") ||
                        sibling.text().toLowerCase().contains("remote"))) {
            return sibling.text();
        }

        return "";
    }

    /**
     * Check if text is a navigation link (not a job).
     */
    private boolean isNavigationLink(String text) {
        String lower = text.toLowerCase();
        return lower.equals("careers") ||
                lower.equals("jobs") ||
                lower.equals("home") ||
                lower.equals("back") ||
                lower.equals("apply") ||
                lower.equals("view all") ||
                lower.equals("see all") ||
                lower.contains("filter") ||
                lower.contains("search");
    }

    @Override
    public String getSourceName() {
        return "BambooHR";
    }
}