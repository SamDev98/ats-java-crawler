package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.atscrawler.util.Http;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JobviteFetcher extends HybridJsonHtmlFetcher {

    private final CrawlerProperties props;

    public JobviteFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() { return props.getJobviteCompanies(); }

    @Override
    protected String buildUrl(String companySlug) {
        // Usa o domínio público da empresa no Jobvite
        return "https://jobs.jobvite.com/" + companySlug + "/";
    }

    @Override
    protected List<Job> parseJson(String company, com.fasterxml.jackson.databind.JsonNode root) {
        return new ArrayList<>();
    }

    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();
        // o Jobvite renderiza <div class="jv-job-list-item"> dentro de <div id="jv-careersite-listings">
        Elements jobs = doc.select("#jv-careersite-listings a");
        for (Element e : jobs) {
            String title = e.text();
            String href = e.absUrl("href");
            if (!title.isBlank() && href.contains("/job/")) {
                out.add(new Job("Jobvite", company, title, href));
            }
        }
        return out;
    }

    @Override
    public String getSourceName() { return "Jobvite"; }
}
