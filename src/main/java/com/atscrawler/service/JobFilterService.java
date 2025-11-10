package com.atscrawler.service;

import com.atscrawler.config.FilterProperties;
import com.atscrawler.model.Job;
import org.springframework.stereotype.Component;

/**
 * Service responsible for filtering jobs based on configured criteria.
 *
 * <p>Filter Rules:
 * <ol>
 *   <li><b>Role Keywords:</b> Must match at least one keyword (java, spring, kotlin, jvm)</li>
 *   <li><b>Include Keywords:</b> Must match at least one keyword (remote, latam, brazil, etc.)</li>
 *   <li><b>Exclude Keywords:</b> Must NOT match any keyword (javascript, frontend, onsite, etc.)</li>
 * </ol>
 *
 * <p>Word Boundary Matching:
 * Uses regex word boundaries (\b) to prevent false positives.
 * Example: "java" matches but "javascript" does not.
 *
 * <p>Search Scope:
 * Filters are applied to: job title + URL + notes (case-insensitive).
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Component
public class JobFilterService {
    private final FilterProperties props;

    /**
     * Constructs a new JobFilterService with filter configuration.
     *
     * @param props the filter properties containing keyword lists
     */
    public JobFilterService(FilterProperties props) {
        this.props = props;
    }

    /**
     * Checks if a job matches all filter criteria.
     *
     * <p>Filtering logic:
     * <ol>
     *   <li>Check role keywords (must match at least one with word boundary)</li>
     *   <li>Check include keywords (must match at least one, or default remote patterns)</li>
     *   <li>Check exclude keywords (must match none)</li>
     * </ol>
     *
     * @param job the job to filter
     * @return true if job passes all filters, false otherwise
     */
    public boolean matches(Job job) {
        String text = (job.getTitle() + " " + job.getUrl() + " " +
                (job.getNotes() == null ? "" : job.getNotes())).toLowerCase();

        // Role keyword check with word boundary to reject "javascript"
        boolean hasJava = props.getRoleKeywords().stream()
                .anyMatch(kw -> text.matches(".*\\b" + kw.toLowerCase() + "\\b.*"));

        if (!hasJava) return false;

        // Remote/location check (use default patterns if include list empty)
        boolean hasRemote = props.getIncludeKeywords().isEmpty()
                ? text.matches(".*(remote|wfh|work from home|anywhere|latam|brazil).*")
                : props.getIncludeKeywords().stream().anyMatch(text::contains);

        if (!hasRemote) return false;

        // Exclude keyword check
        boolean hasExclude = props.getExcludeKeywords().stream()
                .anyMatch(text::contains);

        return !hasExclude;
    }
}