package com.atscrawler.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    // ✅ Inicialização explícita para evitar NPE
    private List<String> greenhouseCompanies = new ArrayList<>();
    private List<String> leverCompanies = new ArrayList<>();
    private List<String> recruiteeCompanies = new ArrayList<>();
    private List<String> breezyCompanies = new ArrayList<>();

    private String cronExpression = "0 0 7 * * *";
    private String cronZone = "UTC";
    private int expiryDays = 30;

    // ✅ Setter com proteção contra null
    public void setGreenhouseCompanies(List<String> companies) {
        this.greenhouseCompanies = companies != null ? companies : new ArrayList<>();
    }
}
