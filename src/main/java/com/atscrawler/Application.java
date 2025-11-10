package com.atscrawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application entry point for ATS Crawler.
 *
 * <p>Bootstraps the core context and enables:
 * <ul>
 *   <li>{@code @EnableConfigurationProperties} — to load crawler and filter configuration</li>
 *   <li>{@code @EnableScheduling} — to allow scheduled job syncs</li>
 * </ul>
 *
 * <p>Executed via standard Spring Boot runtime.
 *
 * @since 0.4.2
 */
@SpringBootApplication
@EnableConfigurationProperties({
        com.atscrawler.config.CrawlerProperties.class,
        com.atscrawler.config.FilterProperties.class
})
@EnableScheduling
public class Application {

    /**
     * Starts the ATS Crawler Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
