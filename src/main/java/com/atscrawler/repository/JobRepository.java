package com.atscrawler.repository;

import com.atscrawler.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.*;

public interface JobRepository extends JpaRepository<Job, UUID> {
    Optional<Job> findByUrl(String url);

    List<Job> findByActiveTrue();

    List<Job> findByActiveTrueAndLastSeenBefore(LocalDate date);

    long countByActiveTrue();
}
