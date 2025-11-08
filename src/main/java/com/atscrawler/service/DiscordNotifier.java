package com.atscrawler.service;

import com.atscrawler.service.JobMergeService.SyncStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Discord notification service.
 * Sends formatted messages about sync results.
 */
@Component
public class DiscordNotifier {
    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);

    @Value("${discord.webhook:}")
    private String webhook;

    private final RestTemplate rest = new RestTemplate();

    /**
     * Send daily summary notification.
     */
    public void sendDailySummary(SyncStats stats) {
        if (webhook == null || webhook.isBlank()) {
            log.debug("Discord webhook not configured, skipping notification");
            return;
        }

        String message = formatSummary(stats);
        send(message);
    }

    /**
     * Send custom message.
     */
    public void send(String message) {
        if (webhook == null || webhook.isBlank()) {
            log.debug("Discord webhook not configured, skipping: {}", message);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> body = Map.of("content", message);

            rest.postForEntity(webhook, new HttpEntity<>(body, headers), String.class);
            log.debug("✅ Discord notification sent");

        } catch (Exception e) {
            log.warn("⚠️ Discord notification failed: {}", e.getMessage());
        }
    }

    /**
     * Format sync stats into readable message.
     */
    private String formatSummary(SyncStats stats) {
        return String.format("""
            📊 **ATS Java Crawler - Daily Summary**
            
            ✨ New jobs: %d
            🔄 Updated: %d
            ♻️ Reactivated: %d
            ⏰ Expired: %d
            
            🎯 Check your Google Sheets for details!
            """,
                stats.getNewJobs(),
                stats.getUpdated(),
                stats.getReactivated(),
                stats.getExpired()
        );
    }
}