package com.atscrawler.util;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * HTTP client with configurable timeouts, retry logic, and random user agents.
 * Optimized for GitHub Actions cron jobs with minimal resource usage.
 */
@Component
public class Http {
    private static final Logger log = LoggerFactory.getLogger(Http.class);

    private final RestTemplate client;
    private final int maxRetries;

    private static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    };

    public Http(
            @Value("${http.connect-timeout:10000}") int connectTimeout,
            @Value("${http.socket-timeout:15000}") int socketTimeout,
            @Value("${http.max-retries:2}") int maxRetries
    ) {
        this.maxRetries = maxRetries;

        RequestConfig config = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(connectTimeout, TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(socketTimeout, TimeUnit.MILLISECONDS))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .disableRedirectHandling() // Handle redirects manually for better control
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        this.client = new RestTemplate(factory);
    }

    /**
     * Performs GET request with retry logic and random user agent.
     */
    public String get(String url) {
        if (url == null || url.isBlank() || !url.startsWith("http")) {
            log.warn("⚠️ Invalid URL: {}", url);
            return null;
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpEntity<Void> entity = getHttpEntity();
                ResponseEntity<String> res = client.exchange(url, HttpMethod.GET, entity, String.class);

                if (res.getStatusCode().is2xxSuccessful()) {
                    return res.getBody();
                } else if (res.getStatusCode().is3xxRedirection()) {
                    String location = res.getHeaders().getFirst(HttpHeaders.LOCATION);
                    if (location != null && !location.isBlank()) {
                        log.debug("🔀 Redirecting from {} to {}", url, location);
                        return get(location); // Follow redirect
                    }
                }

                log.warn("⚠️ HTTP {} for {}", res.getStatusCode().value(), url);
                return null;

            } catch (RestClientException e) {
                if (attempt < maxRetries) {
                    log.debug("⚠️ Attempt {}/{} failed for {}, retrying...", attempt, maxRetries, url);
                    sleep(1000L * attempt); // Exponential backoff
                } else {
                    log.error("❌ HTTP GET failed after {} attempts: {} -> {}", maxRetries, url, e.getMessage());
                }
            }
        }
        return null;
    }

    private HttpEntity<Void> getHttpEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", getRandomUserAgent());
        headers.set("Accept", "text/html,application/json,application/xml;q=0.9,*/*;q=0.8");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept-Encoding", "gzip, deflate");
        headers.set("Cache-Control", "no-cache");
        headers.set("Connection", "keep-alive");

        return new HttpEntity<>(headers);
    }

    private String getRandomUserAgent() {
        return USER_AGENTS[(int) (Math.random() * USER_AGENTS.length)];
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}