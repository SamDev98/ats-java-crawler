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
import java.time.LocalDate;
import java.util.*;

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

    public DailySync(FetcherRegistry registry,
                     JobRepository repo,
                     JobFilters filters,
                     SheetsSyncService sheets,
                     DiscordNotifier discord,
                     FilterProperties filterProps,
                     CrawlerProperties crawlerProps) {
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
        log.info("=== Daily sync start ===");
        log.info("🔧 Include Keywords: {}", filterProps.getIncludeKeywords());
        log.info("🔧 Role Keywords: {}", filterProps.getRoleKeywords());
        log.info("🔧 Greenhouse Companies: {}", crawlerProps.getGreenhouseCompanies());

        List<Job> fetched = registry.runAll();
        log.info("📊 Total fetched before filtering: {}", fetched.size());

        // Apply filters
        List<Job> filtered = fetched.stream()
                .filter(filters::matches)
                .toList();

        log.info("📊 After filters: {}", filtered.size());

        // Merge with DB (upsert logic)
        List<Job> saved = new ArrayList<>();
        for (Job j : filtered) {
            var existing = repo.findByUrl(j.getUrl());
            if (existing.isEmpty()) {
                j.setFirstSeen(LocalDate.now());
                j.setLastSeen(LocalDate.now());
                j.setActive(true);
                saved.add(repo.save(j));
            } else {
                var e = existing.get();
                e.setLastSeen(LocalDate.now());
                repo.save(e);
            }
        }

        // Deactivate expired
        LocalDate limit = LocalDate.now().minusDays(15);
        repo.findAll().forEach(job -> {
            if (job.getLastSeen().isBefore(limit)) {
                job.setActive(false);
                repo.save(job);
            }
        });
        try {
            log.info("📤 Syncing data to Google Sheets...");
            sheets.pushJobsToSheet();
            log.info("✅ Sheets sync completed successfully.");
        } catch (Exception e) {
            log.error("❌ Sheets sync failed: {}", e.getMessage());
        }
        try {
            int updated = sheets.pullStatusUpdatesToDb();
            if (updated > 0)
                log.info("🔁 Pulled {} status updates from Google Sheets to DB.", updated);
        } catch (Exception e) {
            log.error("❌ Failed to pull status updates from Sheets: {}", e.getMessage());
        }

        // Notify Discord
        discord.send("✅ Daily sync done. New: " + saved.size() +
                " | Total active: " + repo.count());

        log.info("✅ Daily sync done. New: {} | Total active: {}", saved.size(), repo.count());
    }
}
