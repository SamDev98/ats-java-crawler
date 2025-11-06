package com.atscrawler.service;

import com.atscrawler.model.Job;
import com.atscrawler.service.fetch.JobFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FetcherRegistry {
    private static final Logger log = LoggerFactory.getLogger(FetcherRegistry.class);
    private final List<JobFetcher> fetchers;

    public FetcherRegistry(List<JobFetcher> fetchers) {
        this.fetchers = fetchers;
    }

    /**
     * Run all fetchers and collect jobs.
     */
    public List<Job> runAll() {
        List<Job> all = new ArrayList<>();
        for (JobFetcher f : fetchers) {
            try {
                log.info("🌐 Fetching from {} ...", f.getSourceName());
                List<Job> jobs = f.fetch();
                log.info("✅ {} returned {} jobs", f.getSourceName(), jobs.size());
                all.addAll(jobs);
            } catch (Exception e) {
                log.warn("⚠️ Fetcher {} failed: {}", f.getSourceName(), e.getMessage());
            }
        }
        log.info("📦 Total collected: {} jobs from {} sources", all.size(), fetchers.size());
        return all;
    }
}
