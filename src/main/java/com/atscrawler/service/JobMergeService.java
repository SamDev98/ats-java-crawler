package com.atscrawler.service;

import com.atscrawler.model.Job;
import com.atscrawler.repository.JobRepository;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class JobMergeService {
    private static final Logger log = LoggerFactory.getLogger(JobMergeService.class);
    private final JobRepository repo;

    public JobMergeService(JobRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public SyncStats mergeWithDatabase(List<Job> jobs) {
        int newCount = 0;
        int updatedCount = 0;
        int reactivatedCount = 0;
        LocalDate today = LocalDate.now();

        for (Job newJob : jobs) {
            Optional<Job> existing = repo.findByUrl(newJob.getUrl());

            if (existing.isEmpty()) {
                // New job
                newJob.setFirstSeen(today);
                newJob.setLastSeen(today);
                newJob.setActive(true);
                repo.save(newJob);
                newCount++;
            } else {
                // Existing job
                Job existingJob = existing.get();
                existingJob.setLastSeen(today);

                if (!existingJob.isActive()) {
                    // Reactivated
                    existingJob.setActive(true);
                    reactivatedCount++;
                } else {
                    // Updated
                    updatedCount++;
                }

                // Preserve manual edits (Status/Notes)
                if (newJob.getStatus() != null && !newJob.getStatus().isBlank()) {
                    existingJob.setStatus(newJob.getStatus());
                }
                if (newJob.getNotes() != null && !newJob.getNotes().isBlank()) {
                    existingJob.setNotes(newJob.getNotes());
                }

                repo.save(existingJob);
            }
        }

        return new SyncStats(newCount, updatedCount, reactivatedCount);
    }

    @Transactional
    public int expireOldJobs() {
        LocalDate cutoff = LocalDate.now().minusDays(30);
        List<Job> stale = repo.findByActiveTrueAndLastSeenBefore(cutoff);

        for (Job job : stale) {
            job.setActive(false);
        }

        repo.saveAll(stale);
        return stale.size();
    }

    // ✅ Inner class agora é package-private ou public
    @Getter
    public static class SyncStats {
        private final int newJobs;
        private final int updated;
        private final int reactivated;
        @Setter
        private int expired;

        public SyncStats(int newJobs, int updated, int reactivated) {
            this.newJobs = newJobs;
            this.updated = updated;
            this.reactivated = reactivated;
        }

        @Override
        public String toString() {
            return String.format(
                    """
                            📊 **Sync Summary**
                            • New: %d
                            • Updated: %d
                            • Reactivated: %d
                            • Expired: %d""",
                    newJobs, updated, reactivated, expired
            );
        }
    }
}