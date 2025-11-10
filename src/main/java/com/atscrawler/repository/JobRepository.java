package com.atscrawler.repository;

import com.atscrawler.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.*;

/**
 * Repository interface for accessing and managing {@link Job} entities.
 *
 * <p>Provides convenient JPA-based query methods for job synchronization,
 * expiration checks, and counting active records.
 *
 * <p>Common queries:
 * <ul>
 *   <li>{@link #findByUrl(String)}</li>
 *   <li>{@link #findByActiveTrue()}</li>
 *   <li>{@link #findByActiveTrueAndLastSeenBefore(LocalDate)}</li>
 * </ul>
 *
 * @author SamDev98
 * @since 0.4.1
 */
public interface JobRepository extends JpaRepository<Job, UUID> {

    /**
     * Finds a job entry by its unique URL.
     *
     * @param url job posting URL
     * @return optional job entity
     */
    Optional<Job> findByUrl(String url);

    /**
     * Retrieves all active jobs.
     *
     * @return list of active jobs
     */
    List<Job> findByActiveTrue();

    /**
     * Finds active jobs that haven’t been seen since a specific date.
     *
     * @param date cutoff date
     * @return list of stale active jobs
     */
    List<Job> findByActiveTrueAndLastSeenBefore(LocalDate date);

    /**
     * Finds recently added active jobs.
     *
     * @param date threshold date
     * @return list of jobs first seen after the given date
     */
    List<Job> findByActiveTrueAndFirstSeenAfter(LocalDate date);

    /**
     * Counts all active job records.
     *
     * @return number of active jobs
     */
    long countByActiveTrue();
}
