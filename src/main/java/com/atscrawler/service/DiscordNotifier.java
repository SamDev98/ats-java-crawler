package com.atscrawler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Component
public class DiscordNotifier {
  private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);
  @Value("${discord.webhook:}") private String webhook;
  private final RestTemplate rest = new RestTemplate();
  public void send(String msg) {
    if (webhook==null || webhook.isBlank()) { log.info("Discord webhook not set; skipping: {}", msg); return; }
    try {
      HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
      rest.postForEntity(webhook, new HttpEntity<>(Map.of("content", msg), h), String.class);
    } catch (Exception e) { log.warn("Discord post failed: {}", e.getMessage()); }
  }
}
