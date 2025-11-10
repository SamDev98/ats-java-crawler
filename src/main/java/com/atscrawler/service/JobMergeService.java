package com.atscrawler.service;

import com.atscrawler.model.Job;
import com.atscrawler.repository.JobRepository;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service responsible for merging fetched jobs with the database.
 *
 * <p>This service compares fetched job data with existing records and:
 * <ul>
 *   <li>Adds new jobs</li>
 *   <li>Updates existing active ones</li>
 *   <li>Reactivates expired ones</li>
 *   <li>Expires jobs not seen in the last 30 days</li>
 * </ul>
 *
 * <p>All operations are performed within transactional boundaries to ensure consistency.
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Service
public class JobMergeService {
    private final JobRepository repo;

    /**
     * Constructs a new {@code JobMergeService}.
     *
     * @param repo the {@link JobRepository} used for persistence operations
     */
    public JobMergeService(JobRepository repo) {
        this.repo = repo;
    }

    /**
     * Merges fetched jobs with the existing database entries.
     *
     * <p>Logic:
     * <ol>
     *   <li>If job does not exist → insert as new</li>
     *   <li>If job exists but inactive → reactivate</li>
     *   <li>If job exists and active → update last seen date</li>
     * </ol>
     *
     * @param jobs list of fetched jobs
     * @return {@link SyncStats} containing statistics of the operation
     */
    @Transactional
    public SyncStats mergeWithDatabase(List<Job> jobs) {
        int newCount = 0;
        int updatedCount = 0;
        int reactivatedCount = 0;
        LocalDate today = LocalDate.now();

        for (Job newJob : jobs) {
            Optional<Job> existing = repo.findByUrl(newJob.getUrl());

            if (existing.isEmpty()) {
                // New job entry
                newJob.setFirstSeen(today);
                newJob.setLastSeen(today);
                newJob.setActive(true);
                repo.save(newJob);
                newCount++;
            } else {
                Job existingJob = existing.get();
                existingJob.setLastSeen(today);

                if (!existingJob.isActive()) {
                    existingJob.setActive(true);
                    reactivatedCount++;
                } else {
                    updatedCount++;
                }

                // Preserve manually edited fields
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

    /**
     * Marks jobs as expired if they have not been seen for 30 days.
     *
     * @return number of expired jobs
     */
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

    /**
     * Holds synchronization statistics for job merging.
     */
    @Getter
    public static class SyncStats {
        private final int newJobs;
        private final int updated;
        private final int reactivated;

        @Setter
        private int expired;

        /**
         * Constructs a new {@code SyncStats} record.
         *
         * @param newJobs      number of new jobs added
         * @param updated      number of existing jobs updated
         * @param reactivated  number of inactive jobs reactivated
         */
        public SyncStats(int newJobs, int updated, int reactivated) {
            this.newJobs = newJobs;
            this.updated = updated;
            this.reactivated = reactivated;
        }

        /**
         * Returns a formatted summary for logs and notifications.
         *
         * @return summary string with emojis and counts
         */
        @Override
        public String toString() {
            return String.format("""
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
