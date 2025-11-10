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
 * Service responsible for managing and executing all {@link JobFetcher} instances in parallel.
 *
 * <p>This registry coordinates the execution of multiple job fetchers concurrently
 * to maximize performance and minimize crawling time. It supports both
 * standard and virtual thread execution (Java 21+).
 *
 * <p>Execution flow:
 * <ol>
 *   <li>Creates a thread pool (fixed or virtual)</li>
 *   <li>Executes all registered fetchers asynchronously</li>
 *   <li>Waits for completion with a configurable timeout</li>
 *   <li>Handles retries and logs detailed results</li>
 * </ol>
 *
 * <p>Environment properties:
 * <ul>
 *   <li>{@code crawler.thread-pool-size} — number of threads (default: 4)</li>
 *   <li>{@code crawler.timeout-minutes} — fetch timeout per source (default: 10)</li>
 * </ul>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Service
public class FetcherRegistry {
    private static final Logger log = LoggerFactory.getLogger(FetcherRegistry.class);

    private final List<JobFetcher> fetchers;
    private final int threadPoolSize;
    private final int timeoutMinutes;

    /**
     * Constructs a new {@code FetcherRegistry} with injected fetchers and configuration.
     *
     * @param fetchers       list of registered {@link JobFetcher} implementations
     * @param threadPoolSize maximum number of threads used for concurrent fetching
     * @param timeoutMinutes timeout (in minutes) for each fetch operation
     */
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
     * Returns the number of registered fetchers.
     *
     * @return total fetchers count
     */
    public int getFetcherCount() {
        return fetchers.size();
    }

    /**
     * Executes all fetchers in parallel and collects the fetched jobs.
     *
     * <p>This method uses a configurable thread pool. If the configured size
     * is zero or negative, virtual threads are used instead for lightweight concurrency.
     *
     * @return list of all jobs fetched from all sources
     */
    public List<Job> runAll() {
        log.info("🌐 Starting parallel fetch from {} sources...", fetchers.size());

        List<Job> allJobs = new CopyOnWriteArrayList<>();

        try (ExecutorService executor = threadPoolSize > 0
                ? Executors.newFixedThreadPool(threadPoolSize)
                : Executors.newVirtualThreadPerTaskExecutor()) {

            List<Future<FetchResult>> futures = new ArrayList<>();

            // Submit all fetchers asynchronously
            for (JobFetcher fetcher : fetchers) {
                futures.add(executor.submit(() -> fetchSafely(fetcher)));
            }

            int completed = 0;
            int failed = 0;

            // Collect and log results
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

        }

        return allJobs;
    }

    /**
     * Executes a single fetcher safely with retry logic and error handling.
     *
     * @param fetcher the fetcher to execute
     * @return {@link FetchResult} containing results or error details
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
                return new FetchResult(fetcher.getSourceName(), jobs, null, true);
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    log.warn("⚠️ Retry {}/{} for {}: {}", attempt, maxRetries, fetcher.getSourceName(), e.getMessage());
                    sleep(2000L * attempt);
                } else {
                    log.error("❌ All retries failed for {}: {}", fetcher.getSourceName(), e.getMessage());
                    return new FetchResult(fetcher.getSourceName(), new ArrayList<>(), e.getMessage(), false);
                }
            }
        }
        return new FetchResult(fetcher.getSourceName(), new ArrayList<>(), "Unknown error", false);
    }

    /**
     * Sleeps for the specified number of milliseconds, ignoring interruptions.
     *
     * @param millis time in milliseconds
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Immutable result container for fetch operations.
     *
     * @param sourceName fetcher source name
     * @param jobs       list of successfully fetched jobs
     * @param error      error message if any
     * @param success    flag indicating whether the operation succeeded
     */
    private record FetchResult(
            String sourceName,
            List<Job> jobs,
            String error,
            boolean success
    ) {
    }
}
