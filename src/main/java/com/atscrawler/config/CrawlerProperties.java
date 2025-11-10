package com.atscrawler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for crawler sources and scheduling.
 *
 * <p>Holds company lists for each ATS integration and global crawler settings.
 *
 * <p>YAML example:
 * <pre>
 * crawler:
 *   greenhouse-companies: [company1, company2]
 *   lever-companies: [company3]
 *   recruitee-companies: [company4]
 *   breezy-companies: [company5]
 *   cron-expression: "0 0 7 * * *"
 *   cron-zone: "UTC"
 *   expiry-days: 30
 * </pre>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    /** List of companies using Greenhouse ATS. */
    private List<String> greenhouseCompanies = new ArrayList<>();

    /** List of companies using Lever ATS. */
    private List<String> leverCompanies = new ArrayList<>();

    /** List of companies using Recruitee ATS. */
    private List<String> recruiteeCompanies = new ArrayList<>();

    /** List of companies using BreezyHR ATS. */
    private List<String> breezyCompanies = new ArrayList<>();

    /** Cron expression for scheduled crawler execution. */
    private String cronExpression = "0 0 7 * * *";

    /** Time zone for the cron job schedule. */
    private String cronZone = "UTC";

    /** Number of days after which inactive jobs expire. */
    private int expiryDays = 30;

    /**
     * Sets Greenhouse company list safely.
     *
     * @param companies list of company slugs
     */
    public void setGreenhouseCompanies(List<String> companies) {
        this.greenhouseCompanies = companies != null ? companies : new ArrayList<>();
    }
}
