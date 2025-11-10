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
 * Service responsible for sending formatted job sync notifications to Discord.
 *
 * <p>This class integrates with Discord Webhooks to post daily summaries
 * and custom messages with job statistics.
 *
 * <p>Features:
 * <ul>
 *   <li>Optional webhook configuration via application properties</li>
 *   <li>Graceful fallback when webhook is not set</li>
 *   <li>Formatted markdown messages with emoji support</li>
 * </ul>
 *
 * <p>Environment variable:
 * <pre>{@code discord.webhook=https://discord.com/api/webhooks/...}</pre>
 *
 * @author SamDev98
 * @since 0.4.1
 */
@Component
public class DiscordNotifier {
    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);

    /** Discord webhook URL (injected from application properties). */
    @Value("${discord.webhook:}")
    private String webhook;

    /** REST client used to send HTTP POST requests to Discord. */
    private final RestTemplate rest = new RestTemplate();

    /**
     * Sends a formatted daily summary message to the configured Discord channel.
     *
     * @param stats synchronization statistics (new, updated, reactivated, expired)
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
     * Sends a plain text or markdown message to Discord.
     *
     * @param message message content
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
     * Formats synchronization statistics into a Discord-readable message.
     *
     * @param stats synchronization summary data
     * @return formatted Discord message (Markdown + emojis)
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
