package com.atscrawler.scheduler;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.config.FilterProperties;
import com.atscrawler.model.Job;
import com.atscrawler.repository.JobRepository;
import com.atscrawler.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates daily sync workflow.
 * Follows Single Responsibility Principle - only coordinates other services.
 */
@Component
public class DailySync {
    private static final Logger log = LoggerFactory.getLogger(DailySync.class);

    private final FetcherRegistry registry;
    private final JobRepository repo;
    private final JobFilterService filters;
    private final JobMergeService mergeService;
    private final SheetsSyncService sheets;
    private final DiscordNotifier discord;
    private final FilterProperties filterProps;
    private final CrawlerProperties crawlerProps;

    public DailySync(
            FetcherRegistry registry,
            JobRepository repo,
            JobFilterService filters,
            JobMergeService mergeService,
            SheetsSyncService sheets,
            DiscordNotifier discord,
            FilterProperties filterProps,
            CrawlerProperties crawlerProps
    ) {
        this.registry = registry;
        this.repo = repo;
        this.filters = filters;
        this.mergeService = mergeService;
        this.sheets = sheets;
        this.discord = discord;
        this.filterProps = filterProps;
        this.crawlerProps = crawlerProps;
    }

    @Scheduled(cron = "${crawler.cron-expression}")
    public void scheduledSync() {
        runSync();
    }

    /**
     * Main sync workflow.
     * Public for manual trigger via REST controller.
     */
    public SyncResult runSync() {
        long start = System.currentTimeMillis();
        logSyncStart();

        try {
            // 1. Fetch
            List<Job> allJobs = fetchJobs();

            // 2. Filter
            List<Job> filtered = filterJobs(allJobs);

            // 3. Merge with DB
            JobMergeService.SyncStats stats = mergeService.mergeWithDatabase(filtered);
            logMergeResults(stats);

            // 4. Expire old jobs
            int expired = mergeService.expireOldJobs();
            stats.setExpired(expired);
            logExpireResults(expired);

            // 5. Sync to Sheets
            syncToSheets();

            // 6. Pull updates from Sheets
            pullFromSheets();

            // 7. Notify
            notifyCompletion(stats);

            // Summary
            long duration = (System.currentTimeMillis() - start) / 1000;
            logSummary(stats, duration);

            return new SyncResult(true, stats, duration);

        } catch (Exception e) {
            log.error("❌ Sync failed: {}", e.getMessage(), e);
            return new SyncResult(false, null, 0);
        }
    }

    private List<Job> fetchJobs() {
        log.info("====================================");
        log.info("📥 STEP 1: Fetching jobs from sources");
        log.info("====================================");

        List<Job> allJobs = registry.runAll();  // ✅ FIX: Tipo correto
        log.info("📊 Total fetched: {} jobs", allJobs.size());

        return allJobs;
    }

    private List<Job> filterJobs(List<Job> jobs) {
        log.info("====================================");
        log.info("🔍 STEP 2: Applying filters");
        log.info("====================================");

        List<Job> filtered = jobs.stream()
                .filter(filters::matches)
                .collect(Collectors.toList());

        int rejected = jobs.size() - filtered.size();
        log.info("📊 Filtered: {} jobs passed, {} rejected", filtered.size(), rejected);

        return filtered;
    }

    private void logSyncStart() {
        log.info("====================================");
        log.info("🚀 DAILY SYNC STARTED");
        log.info("====================================");
        log.info("🔧 Active Filters:");
        log.info("  → Role Keywords: {}", filterProps.getRoleKeywords());
        log.info("  → Include Keywords: {}", filterProps.getIncludeKeywords());
        log.info("  → Exclude Keywords: {}", filterProps.getExcludeKeywords());
        log.info("🔧 Active ATS:");
        log.info("  → Greenhouse: {}", crawlerProps.getGreenhouseCompanies());
        log.info("  → Lever: {}", crawlerProps.getLeverCompanies());
        log.info("  → Recruitee: {}", crawlerProps.getRecruiteeCompanies());
        log.info("  → BreezyHR: {}", crawlerProps.getBreezyCompanies());
    }

    private void logMergeResults(JobMergeService.SyncStats stats) {
        log.info("====================================");
        log.info("💾 STEP 3: Merging with database");
        log.info("====================================");
        log.info("💾 Saved {} jobs to database",
                stats.getNewJobs() + stats.getUpdated() + stats.getReactivated());
    }

    private void logExpireResults(int expired) {
        log.info("====================================");
        log.info("⏰ STEP 4: Expiring old jobs");
        log.info("====================================");
        if (expired > 0) {
            log.info("⏰ Expired {} old jobs", expired);
        } else {
            log.info("⏰ No jobs to expire");
        }
    }

    private void syncToSheets() {
        log.info("====================================");
        log.info("📤 STEP 5: Syncing to Google Sheets");
        log.info("====================================");
        sheets.pushJobsToSheet();
        log.info("✅ Sheets sync completed");
    }

    private void pullFromSheets() {
        log.info("====================================");
        log.info("📥 STEP 6: Pulling updates from Sheets");
        log.info("====================================");
        int pulled = sheets.pullStatusUpdatesToDb();
        if (pulled > 0) {
            log.info("🔁 Pulled {} updates from Sheets", pulled);
        } else {
            log.info("🔁 No status updates in Sheets");
        }
    }

    private void notifyCompletion(JobMergeService.SyncStats stats) {
        log.info("====================================");
        log.info("📣 STEP 7: Sending notifications");
        log.info("====================================");
        discord.send(stats.toString());
        log.info("📣 Discord notification sent");
    }

    private void logSummary(JobMergeService.SyncStats stats, long duration) {
        log.info("====================================");
        log.info("✅ DAILY SYNC COMPLETED");
        log.info("====================================");
        log.info("📊 Summary:");
        log.info("  → New jobs: {}", stats.getNewJobs());
        log.info("  → Updated: {}", stats.getUpdated());
        log.info("  → Reactivated: {}", stats.getReactivated());
        log.info("  → Expired: {}", stats.getExpired());
        log.info("  → Total active: {}", repo.countByActiveTrue());
        log.info("⏱️ Duration: {} seconds", duration);
        log.info("====================================");
    }

    /**
     * Result object for sync operation.
     */
    public record SyncResult(
            boolean success,
            JobMergeService.SyncStats stats,
            long durationSeconds
    ) {}
}