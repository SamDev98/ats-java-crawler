package com.atscrawler.controller;

import com.atscrawler.model.Job;
import com.atscrawler.repository.JobRepository;
import com.atscrawler.scheduler.DailySync;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ManualController {
    private final DailySync scheduler;
    private final JobRepository repo;

    public ManualController(DailySync scheduler, JobRepository repo) {
        this.scheduler = scheduler;
        this.repo = repo;
    }

    @GetMapping("/run-now")
    public ResponseEntity<String> runNow() {
        scheduler.scheduledSync();
        return ResponseEntity.ok("✅ Daily sync triggered");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("totalJobs", repo.count());
        health.put("activeJobs", repo.findByActiveTrue().size());
        health.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(health);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        List<Job> all = repo.findAll();
        Map<String, Long> stats = all.stream()
                .collect(Collectors.groupingBy(Job::getSource, Collectors.counting()));
        return ResponseEntity.ok(stats);
    }
}
