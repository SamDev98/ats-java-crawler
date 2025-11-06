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
public class TaleoFetcher extends HybridJsonHtmlFetcher {

    private final CrawlerProperties props;

    public TaleoFetcher(CrawlerProperties props, Http http) {
        super(http);
        this.props = props;
    }

    @Override
    protected List<String> getCompanySlugs() { return props.getTaleoCompanies(); }

    @Override
    protected String buildUrl(String companySlug) {
        // Usa o portal público de busca da Taleo
        return "https://" + companySlug + ".taleo.net/careersection/2/jobsearch.ftl";
    }

    @Override
    protected List<Job> parseJson(String company, com.fasterxml.jackson.databind.JsonNode root) {
        return new ArrayList<>();
    }

    @Override
    protected List<Job> parseHtml(String company, Document doc) {
        List<Job> out = new ArrayList<>();
        Elements rows = doc.select("table[id=jobs] tr[id^=row]");
        for (Element r : rows) {
            String title = r.select("span[id^=jobTitle]").text();
            String href = r.select("a[id^=jobLink]").attr("href");
            if (!title.isBlank() && !href.isBlank()) {
                out.add(new Job("Taleo", company, title, "https://" + company + ".taleo.net" + href));
            }
        }
        return out;
    }

    @Override
    public String getSourceName() { return "Taleo"; }
}
