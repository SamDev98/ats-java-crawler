package com.atscrawler.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for job filtering.
 *
 * <p>Defines keyword lists for inclusion, exclusion, and role filtering.
 * Used by {@code JobFilterService} to evaluate job titles, notes, and URLs.
 *
 * <p>YAML example:
 * <pre>
 * filter:
 *   include-keywords: [remote, latam]
 *   exclude-keywords: [javascript, frontend]
 *   role-keywords: [java, spring]
 * </pre>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Getter
@ConfigurationProperties(prefix = "filter")
public class FilterProperties {

    /** Keywords that must appear in job content (e.g., remote, latam). */
    private List<String> includeKeywords = new ArrayList<>();

    /** Keywords that disqualify a job (e.g., javascript, frontend). */
    private List<String> excludeKeywords = new ArrayList<>();

    /** Role-specific keywords required for matching (e.g., java, kotlin). */
    private List<String> roleKeywords = new ArrayList<>();

    /**
     * Sets include keywords safely, avoiding null assignment.
     */
    public void setIncludeKeywords(List<String> includeKeywords) {
        this.includeKeywords = includeKeywords != null ? includeKeywords : new ArrayList<>();
    }

    /**
     * Sets exclude keywords safely, avoiding null assignment.
     */
    public void setExcludeKeywords(List<String> excludeKeywords) {
        this.excludeKeywords = excludeKeywords != null ? excludeKeywords : new ArrayList<>();
    }

    /**
     * Sets role keywords safely, avoiding null assignment.
     */
    public void setRoleKeywords(List<String> roleKeywords) {
        this.roleKeywords = roleKeywords != null ? roleKeywords : new ArrayList<>();
    }
}
