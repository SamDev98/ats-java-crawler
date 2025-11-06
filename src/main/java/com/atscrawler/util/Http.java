package com.atscrawler.util;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class Http {

    private final RestTemplate client;

    public Http() {
        // Configura timeout e redirecionamento automático
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setConnectionRequestTimeout(10_000)
                .setSocketTimeout(10_000)
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .setRedirectStrategy(new LaxRedirectStrategy()) // segue redirects 301/302
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        this.client = new RestTemplate(factory);
    }

    public String get(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/121.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/json;q=0.9,*/*;q=0.8");
            headers.set("Accept-Language", "en-US,en;q=0.9");
            headers.set("Cache-Control", "no-cache");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> res = client.exchange(url, HttpMethod.GET, entity, String.class);

            if (res.getStatusCode().is2xxSuccessful()) {
                return res.getBody();
            } else {
                System.err.println("⚠️ HTTP " + res.getStatusCodeValue() + " for " + url);
                return null;
            }
        } catch (RestClientException e) {
            System.err.println("⚠️ HTTP GET failed " + url + " -> " + e.getMessage());
            return null;
        }
    }
}
