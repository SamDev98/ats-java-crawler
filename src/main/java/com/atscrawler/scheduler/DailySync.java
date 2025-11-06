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
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Daily sync scheduler optimized for GitHub Actions cron jobs.
 * Features:
 * - Batch processing for database operations
 * - Memory-efficient filtering
 * - Detailed metrics logging
 */
@Component
public class DailySync {
    private static final Logger log = LoggerFactory.getLogger(DailySync.class);

    private final FetcherRegistry registry;
    private final JobRepository repo;
    private final JobFilters filters;
    private final SheetsSyncService sheets;
    private final DiscordNotifier discord;
    private final FilterProperties filterProps;
    private final CrawlerProperties crawlerProps;

    public DailySync(
            FetcherRegistry registry,
            JobRepository repo,
            JobFilters filters,
            SheetsSyncService sheets,
            DiscordNotifier discord,
            FilterProperties filterProps,
            CrawlerProperties crawlerProps
    ) {
        this.registry = registry;
        this.repo = repo;
        this.filters = filters;
        this.sheets = sheets;
        this.discord = discord;
        this.filterProps = filterProps;
        this.crawlerProps = crawlerProps;
    }

    @Scheduled(cron = "${crawler.cron-expression:0 0 7 * * *}", zone = "${crawler.cron-zone:UTC}")
    public void runDailySync() {
        Instant start = Instant.now();
        log.info("====================================");
        log.info("🚀 DAILY SYNC STARTED");
        log.info("====================================");

        logConfiguration();

        try {
            // 1. Fetch jobs from all sources
            List<Job> fetched = fetchJobs();

            // 2. Apply filters
            List<Job> filtered = filterJobs(fetched);

            // 3. Merge with database
            SyncStats stats = mergeWithDatabase(filtered);

            // 4. Expire old jobs
            int expired = expireOldJobs();
            stats.setExpired(expired);

            // 5. Sync to Google Sheets
            syncToSheets();

            // 6. Pull status updates from Sheets
            pullUpdatesFromSheets();

            // 7. Send notifications
            sendNotifications(stats);

            // 8. Log summary
            logSummary(stats, start);

        } catch (Exception e) {
            log.error("❌ CRITICAL ERROR in daily sync: {}", e.getMessage(), e);
            discord.send("❌ Daily sync failed: " + e.getMessage());
        }
    }

    private void logConfiguration() {
        log.info("🔧 Active Filters:");
        log.info("  → Role Keywords: {}", filterProps.getRoleKeywords());
        log.info("  → Include Keywords: {}", filterProps.getIncludeKeywords());
        log.info("  → Exclude Keywords: {}", filterProps.getExcludeKeywords());
        log.info("🔧 Active ATS:");
        log.info("  → Greenhouse: {}", crawlerProps.getGreenhouseCompanies());
        log.info("  → Lever: {}", crawlerProps.getLeverCompanies());
        log.info("  → Workable: {}", crawlerProps.getWorkableCompanies());
        log.info("  → Recruitee: {}", crawlerProps.getRecruiteeCompanies());
        log.info("  → Ashby: {}", crawlerProps.getAshbyCompanies());
        log.info("  → BreezyHR: {}", crawlerProps.getBreezyCompanies());
        log.info("  → Teamtailor: {}", crawlerProps.getTeamtailorCompanies());
        log.info("  → Jobvite: {}", crawlerProps.getJobviteCompanies());
        log.info("  → BambooHR: {}", crawlerProps.getBamboohrCompanies());
    }

    private List<Job> fetchJobs() {
        log.info("====================================");
        log.info("📥 STEP 1: Fetching jobs from sources");
        log.info("====================================");

        List<Job> jobs = registry.runAll();
        log.info("📊 Total fetched: {} jobs", jobs.size());
        return jobs;
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

    @Transactional
    protected SyncStats mergeWithDatabase(List<Job> filtered) {
        log.info("====================================");
        log.info("💾 STEP 3: Merging with database");
        log.info("====================================");

        SyncStats stats = new SyncStats();
        LocalDate today = LocalDate.now();

        // Build a map of existing URLs for fast lookup
        Map<String, Job> existingJobs = repo.findAll().stream()
                .collect(Collectors.toMap(Job::getUrl, j -> j, (a, b) -> a));

        List<Job> toSave = new ArrayList<>();

        for (Job newJob : filtered) {
            try {
                Job existing = existingJobs.get(newJob.getUrl());

                if (existing == null) {
                    // New job
                    newJob.setFirstSeen(today);
                    newJob.setLastSeen(today);
                    newJob.setActive(true);
                    toSave.add(newJob);
                    stats.incrementNew();
                } else {
                    // Existing job - update last seen
                    existing.setLastSeen(today);
                    if (!existing.isActive()) {
                        existing.setActive(true);
                        stats.incrementReactivated();
                    }
                    toSave.add(existing);
                    stats.incrementUpdated();
                }
            } catch (Exception e) {
                log.warn("⚠️ Failed to process job {}: {}", newJob.getUrl(), e.getMessage());
                stats.incrementErrors();
            }
        }

        // Batch save
        if (!toSave.isEmpty()) {
            repo.saveAll(toSave);
            log.info("💾 Saved {} jobs to database", toSave.size());
        }

        return stats;
    }

    @Transactional
    protected int expireOldJobs() {
        log.info("====================================");
        log.info("⏰ STEP 4: Expiring old jobs");
        log.info("====================================");

        LocalDate cutoff = LocalDate.now().minusDays(15);
        List<Job> toExpire = repo.findByActiveTrue().stream()
                .filter(j -> j.getLastSeen().isBefore(cutoff))
                .collect(Collectors.toList());

        toExpire.forEach(j -> j.setActive(false));

        if (!toExpire.isEmpty()) {
            repo.saveAll(toExpire);
            log.info("⏰ Expired {} jobs (last seen before {})", toExpire.size(), cutoff);
        } else {
            log.info("⏰ No jobs to expire");
        }

        return toExpire.size();
    }

    private void syncToSheets() {
        log.info("====================================");
        log.info("📤 STEP 5: Syncing to Google Sheets");
        log.info("====================================");

        try {
            sheets.pushJobsToSheet();
            log.info("✅ Sheets sync completed");
        } catch (Exception e) {
            log.error("❌ Sheets sync failed: {}", e.getMessage());
        }
    }

    private void pullUpdatesFromSheets() {
        log.info("====================================");
        log.info("📥 STEP 6: Pulling updates from Sheets");
        log.info("====================================");

        try {
            int updated = sheets.pullStatusUpdatesToDb();
            if (updated > 0) {
                log.info("🔁 Pulled {} status updates from Sheets", updated);
            } else {
                log.info("🔁 No status updates in Sheets");
            }
        } catch (Exception e) {
            log.error("❌ Failed to pull updates: {}", e.getMessage());
        }
    }

    private void sendNotifications(SyncStats stats) {
        log.info("====================================");
        log.info("📣 STEP 7: Sending notifications");
        log.info("====================================");

        String message = String.format(
                """
                        ✅ Daily Sync Complete
                        🆕 New: %d
                        🔄 Updated: %d
                        ♻️ Reactivated: %d
                        ⏰ Expired: %d
                        📊 Total Active: %d""",
                stats.getNewJobs(),
                stats.getUpdated(),
                stats.getReactivated(),
                stats.getExpired(),
                repo.findByActiveTrue().size()
        );

        discord.send(message);
        log.info("📣 Discord notification sent");
    }

    private void logSummary(SyncStats stats, Instant start) {
        Duration duration = Duration.between(start, Instant.now());

        log.info("====================================");
        log.info("✅ DAILY SYNC COMPLETED");
        log.info("====================================");
        log.info("📊 Summary:");
        log.info("  → New jobs: {}", stats.getNewJobs());
        log.info("  → Updated: {}", stats.getUpdated());
        log.info("  → Reactivated: {}", stats.getReactivated());
        log.info("  → Expired: {}", stats.getExpired());
        log.info("  → Errors: {}", stats.getErrors());
        log.info("  → Total active: {}", repo.findByActiveTrue().size());
        log.info("⏱️ Duration: {} seconds", duration.getSeconds());
        log.info("====================================");
    }

    /**
     * Statistics holder for sync operations.
     */
    private static class SyncStats {
        private int newJobs = 0;
        private int updated = 0;
        private int reactivated = 0;
        private int expired = 0;
        private int errors = 0;

        void incrementNew() { newJobs++; }
        void incrementUpdated() { updated++; }
        void incrementReactivated() { reactivated++; }
        void incrementErrors() { errors++; }
        void setExpired(int count) { expired = count; }

        int getNewJobs() { return newJobs; }
        int getUpdated() { return updated; }
        int getReactivated() { return reactivated; }
        int getExpired() { return expired; }
        int getErrors() { return errors; }
    }
}