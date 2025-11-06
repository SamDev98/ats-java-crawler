package com.atscrawler.service;

import com.atscrawler.model.Job;
import com.atscrawler.service.fetch.JobFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Registry that runs all fetchers in parallel for better performance.
 * Optimized for GitHub Actions with configurable thread pool.
 */
@Service
public class FetcherRegistry {
    private static final Logger log = LoggerFactory.getLogger(FetcherRegistry.class);

    private final List<JobFetcher> fetchers;
    private final int threadPoolSize;
    private final int timeoutMinutes;

    public FetcherRegistry(
            List<JobFetcher> fetchers,
            @Value("${crawler.thread-pool-size:4}") int threadPoolSize,
            @Value("${crawler.timeout-minutes:10}") int timeoutMinutes
    ) {
        this.fetchers = fetchers;
        this.threadPoolSize = threadPoolSize;
        this.timeoutMinutes = timeoutMinutes;

        log.info("🚀 Initialized FetcherRegistry with {} fetchers", fetchers.size());
        log.info("⚙️ Thread pool size: {}, Timeout: {} minutes", threadPoolSize, timeoutMinutes);
    }

    /**
     * Run all fetchers in parallel and collect jobs.
     * Uses virtual threads (Java 21+) for efficiency.
     */
    public List<Job> runAll() {
        log.info("🌐 Starting parallel fetch from {} sources...", fetchers.size());

        List<Job> allJobs = new CopyOnWriteArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<FetchResult>> futures = new ArrayList<>();

        // Submit all fetchers
        for (JobFetcher fetcher : fetchers) {
            futures.add(executor.submit(() -> fetchSafely(fetcher)));
        }

        // Collect results with timeout
        int completed = 0;
        int failed = 0;

        for (Future<FetchResult> future : futures) {
            try {
                FetchResult result = future.get(timeoutMinutes, TimeUnit.MINUTES);
                if (result.success()) {
                    allJobs.addAll(result.jobs());
                    completed++;
                    log.info("✅ {} returned {} jobs", result.sourceName(), result.jobs().size());
                } else {
                    failed++;
                    log.warn("⚠️ {} failed: {}", result.sourceName(), result.error());
                }
            } catch (TimeoutException e) {
                failed++;
                log.error("⏱️ Fetcher timed out after {} minutes", timeoutMinutes);
                future.cancel(true);
            } catch (Exception e) {
                failed++;
                log.error("❌ Error collecting result: {}", e.getMessage());
            }
        }

        executor.shutdown();

        log.info("📦 Fetch completed: {} successful, {} failed, {} total jobs",
                completed, failed, allJobs.size());

        return allJobs;
    }

    /**
     * Safely execute a fetcher with error handling.
     */
    private FetchResult fetchSafely(JobFetcher fetcher) {
        try {
            log.debug("🌐 Starting fetch from {}...", fetcher.getSourceName());
            List<Job> jobs = fetcher.fetch();
            return new FetchResult(fetcher.getSourceName(), jobs, null, true);
        } catch (Exception e) {
            log.error("❌ Fetcher {} threw exception: {}",
                    fetcher.getSourceName(), e.getMessage(), e);
            return new FetchResult(fetcher.getSourceName(), new ArrayList<>(),
                    e.getMessage(), false);
        }
    }

    /**
     * Result record for fetch operations.
     */
    private record FetchResult(
            String sourceName,
            List<Job> jobs,
            String error,
            boolean success
    ) {}
}