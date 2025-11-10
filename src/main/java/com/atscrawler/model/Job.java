package com.atscrawler.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a Job entity stored in the database.
 *
 * <p>This class defines a job listing fetched by the crawler, with validation
 * rules and indexed fields optimized for PostgreSQL performance.
 *
 * <p>Key Features:
 * <ul>
 *   <li>Automatic UUID generation as primary key</li>
 *   <li>Validation constraints for all required fields</li>
 *   <li>Normalized string inputs (trimmed and validated)</li>
 *   <li>Unique URL constraint for deduplication</li>
 * </ul>
 *
 * <p>Common indexes:
 * <ul>
 *   <li>Job activity and visibility (is_active, last_seen)</li>
 *   <li>Search by company and title</li>
 *   <li>Deduplication by URL</li>
 * </ul>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Setter
@Getter
@NoArgsConstructor
@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_jobs_url", columnList = "url", unique = true),
        @Index(name = "idx_jobs_active", columnList = "is_active"),
        @Index(name = "idx_jobs_last_seen", columnList = "last_seen"),
        @Index(name = "idx_jobs_source", columnList = "source"),
        @Index(name = "idx_jobs_active_last_seen", columnList = "is_active, last_seen"),
        @Index(name = "idx_jobs_company_title", columnList = "company, title")
})
public class Job {

    /** Unique job identifier. */
    @Id
    @GeneratedValue
    private UUID id;

    /** Job source (e.g., website or API). */
    @NotBlank(message = "Source cannot be blank")
    @Size(max = 100, message = "Source must be less than 100 characters")
    @Column(nullable = false, length = 100)
    private String source;

    /** Company name offering the job. */
    @NotBlank(message = "Company cannot be blank")
    @Size(max = 200, message = "Company must be less than 200 characters")
    @Column(nullable = false, length = 200)
    private String company;

    /** Job title or position. */
    @NotBlank(message = "Title cannot be blank")
    @Size(max = 300, message = "Title must be less than 300 characters")
    @Column(nullable = false, length = 300)
    private String title;

    /** URL of the job posting. Must be unique. */
    @NotBlank(message = "URL cannot be blank")
    @Size(max = 2048, message = "URL must be less than 2048 characters")
    @Column(length = 2048, nullable = false, unique = true)
    private String url;

    /** First time the job was seen by the crawler. */
    @Column(name = "first_seen")
    private LocalDate firstSeen;

    /** Last time the job was seen by the crawler. */
    @Column(name = "last_seen")
    private LocalDate lastSeen;

    /** Indicates whether the job is still active. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Job status (default: "Awaiting"). */
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String status = "Awaiting";

    /** Optional notes about the job entry. */
    @Size(max = 2048, message = "Notes must be less than 2048 characters")
    @Column(length = 2048)
    private String notes;

    /**
     * Constructs a new Job with the essential fields.
     *
     * @param source  the job source
     * @param company the company name
     * @param title   the job title
     * @param url     the job posting URL
     * @throws IllegalArgumentException if any argument is null, blank, or invalid
     */
    public Job(String source, String company, String title, String url) {
        this.source = validateAndTrim(source, "source");
        this.company = validateAndTrim(company, "company");
        this.title = validateAndTrim(title, "title");
        this.url = validateUrl(url);
    }

    /**
     * Validates and trims a string field.
     *
     * @param value     the input string
     * @param fieldName the field name for error messages
     * @return trimmed and validated string
     * @throws IllegalArgumentException if null or blank
     */
    private String validateAndTrim(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }

    /**
     * Validates the job URL format.
     *
     * @param url the input URL
     * @return validated and trimmed URL
     * @throws IllegalArgumentException if invalid or too long
     */
    private String validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        String trimmed = url.trim();

        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }

        if (trimmed.length() > 2048) {
            throw new IllegalArgumentException("URL too long (max 2048 characters)");
        }

        return trimmed;
    }

    /**
     * Sets a validated URL.
     *
     * @param url the new job URL
     */
    public void setUrl(String url) {
        this.url = validateUrl(url);
    }

    /**
     * Sets the job title with trimming.
     *
     * @param title the job title
     */
    public void setTitle(String title) {
        this.title = validateAndTrim(title, "title");
    }

    /**
     * Sets the company name with trimming.
     *
     * @param company the company name
     */
    public void setCompany(String company) {
        this.company = validateAndTrim(company, "company");
    }

    /**
     * Sets the source name with trimming.
     *
     * @param source the source name
     */
    public void setSource(String source) {
        this.source = validateAndTrim(source, "source");
    }

    /**
     * Returns a human-readable job summary.
     *
     * @return formatted job information
     */
    @Override
    public String toString() {
        return String.format("Job[%s] %s - %s (%s)", source, company, title,
                url.length() > 50 ? url.substring(0, 50) + "..." : url);
    }

    /**
     * Compares jobs by URL equality.
     *
     * @param o other object
     * @return true if both jobs share the same URL
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Job job)) return false;
        return url != null && url.equals(job.url);
    }

    /**
     * Computes hash code based on URL.
     *
     * @return hash code value
     */
    @Override
    public int hashCode() {
        return url != null ? url.hashCode() : 0;
    }
}
