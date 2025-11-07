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
 * Job entity with validation constraints.
 * Optimized for PostgreSQL with proper indexes.
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

    @Id
    @GeneratedValue
    private UUID id;

    @NotBlank(message = "Source cannot be blank")
    @Size(max = 100, message = "Source must be less than 100 characters")
    @Column(nullable = false, length = 100)
    private String source;

    @NotBlank(message = "Company cannot be blank")
    @Size(max = 200, message = "Company must be less than 200 characters")
    @Column(nullable = false, length = 200)
    private String company;

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 300, message = "Title must be less than 300 characters")
    @Column(nullable = false, length = 300)
    private String title;

    @NotBlank(message = "URL cannot be blank")
    @Size(max = 2048, message = "URL must be less than 2048 characters")
    @Column(length = 2048, nullable = false, unique = true)
    private String url;

    @Column(name = "first_seen")
    private LocalDate firstSeen;

    @Column(name = "last_seen")
    private LocalDate lastSeen;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String status = "Awaiting";

    @Size(max = 2048, message = "Notes must be less than 2048 characters")
    @Column(length = 2048)
    private String notes;

    /**
     * Constructor with basic required fields.
     */
    public Job(String source, String company, String title, String url) {
        this.source = validateAndTrim(source, "source");
        this.company = validateAndTrim(company, "company");
        this.title = validateAndTrim(title, "title");
        this.url = validateUrl(url);
    }

    /**
     * Validates and trims a string field.
     */
    private String validateAndTrim(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
        return value.trim();
    }

    /**
     * Validates URL format.
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
     * Sets URL with validation.
     */
    public void setUrl(String url) {
        this.url = validateUrl(url);
    }

    /**
     * Sets title with trimming.
     */
    public void setTitle(String title) {
        this.title = validateAndTrim(title, "title");
    }

    /**
     * Sets company with trimming.
     */
    public void setCompany(String company) {
        this.company = validateAndTrim(company, "company");
    }

    /**
     * Sets source with trimming.
     */
    public void setSource(String source) {
        this.source = validateAndTrim(source, "source");
    }

    @Override
    public String toString() {
        return String.format("Job[%s] %s - %s (%s)", source, company, title,
                url.length() > 50 ? url.substring(0, 50) + "..." : url);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Job job)) return false;
        return url != null && url.equals(job.url);
    }

    @Override
    public int hashCode() {
        return url != null ? url.hashCode() : 0;
    }
}