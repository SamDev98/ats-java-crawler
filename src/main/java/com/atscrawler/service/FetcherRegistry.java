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
    public int getFetcherCount() {
        return fetchers.size();
    }
    /**
     * Run all fetchers in parallel and collect jobs.
     * Uses virtual threads (Java 21+) for efficiency.
     */
    public List<Job> runAll() {
        log.info("🌐 Starting parallel fetch from {} sources...", fetchers.size());

        List<Job> allJobs = new CopyOnWriteArrayList<>();

        // ✅ FIX: try-with-resources para ExecutorService
        try (ExecutorService executor = threadPoolSize > 0
                ? Executors.newFixedThreadPool(threadPoolSize)
                : Executors.newVirtualThreadPerTaskExecutor()) {

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

            log.info("📦 Fetch completed: {} successful, {} failed, {} total jobs",
                    completed, failed, allJobs.size());

        } // ✅ ExecutorService.close() chamado automaticamente aqui

        return allJobs;
    }

    /**
     * Safely execute a fetcher with error handling.
     */
    private FetchResult fetchSafely(JobFetcher fetcher) {
        long start = System.currentTimeMillis();
        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("🌐 Attempt {}/{} for {}...", attempt, maxRetries, fetcher.getSourceName());
                List<Job> jobs = fetcher.fetch();
                long duration = System.currentTimeMillis() - start;
                log.info("⏱️ {} completed in {}ms ({} jobs)", fetcher.getSourceName(), duration, jobs.size());
                return new FetchResult(fetcher.getSourceName(), jobs, null, true);            } catch (Exception e) {
                if (attempt < maxRetries) {
                    log.warn("⚠️ Retry {}/{} for {}: {}", attempt, maxRetries, fetcher.getSourceName(), e.getMessage());
                    sleep(2000L * attempt); // backoff
                } else {
                    log.error("❌ All retries failed for {}: {}", fetcher.getSourceName(), e.getMessage());
                    return new FetchResult(fetcher.getSourceName(), new ArrayList<>(), e.getMessage(), false);
                }
            }
        }
        return new FetchResult(fetcher.getSourceName(), new ArrayList<>(), "Unknown error", false);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
    ) {
    }
}