package com.atscrawler.controller;

import com.atscrawler.model.Job;
import com.atscrawler.repository.JobRepository;
import com.atscrawler.scheduler.DailySync;
import com.atscrawler.service.JobMergeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller exposing endpoints for manual job synchronization and system health checks.
 *
 * <p>Used primarily for on-demand sync runs and quick API-level diagnostics.
 * Separated from {@link DailySync} to maintain the Single Responsibility Principle.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code GET /api/run-now} — triggers an immediate synchronization</li>
 *   <li>{@code GET /api/health} — provides system and job count info</li>
 *   <li>{@code GET /api/stats} — returns job count per source</li>
 * </ul>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@RestController
@RequestMapping("/api")
public class ManualSyncController {
    private final DailySync scheduler;
    private final JobRepository repo;

    /**
     * Constructs a new manual sync controller.
     *
     * @param scheduler daily synchronization service
     * @param repo      job repository for database access
     */
    public ManualSyncController(DailySync scheduler, JobRepository repo) {
        this.scheduler = scheduler;
        this.repo = repo;
    }

    /**
     * Runs the synchronization process manually via API.
     *
     * @return result summary containing sync stats and job counts
     */
    @GetMapping("/run-now")
    public ResponseEntity<Map<String, Object>> runNow() {
        DailySync.SyncResult result = scheduler.runSync();

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.success());
        response.put("duration", result.durationSeconds() + "s");

        if (result.stats() != null) {
            JobMergeService.SyncStats stats = result.stats();
            response.put("new", stats.getNewJobs());
            response.put("updated", stats.getUpdated());
            response.put("reactivated", stats.getReactivated());
            response.put("expired", stats.getExpired());
        }

        response.put("totalActive", repo.countByActiveTrue());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    /**
     * Returns basic application health status and counts.
     *
     * @return health status JSON
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("totalJobs", repo.count());
        health.put("activeJobs", repo.countByActiveTrue());
        health.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(health);
    }

    /**
     * Returns job count grouped by source system.
     *
     * @return map of source name to job count
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        List<Job> all = repo.findAll();
        Map<String, Long> stats = all.stream()
                .collect(Collectors.groupingBy(Job::getSource, Collectors.counting()));
        return ResponseEntity.ok(stats);
    }
}
