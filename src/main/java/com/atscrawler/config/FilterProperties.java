package com.atscrawler.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@ConfigurationProperties(prefix = "filter")
public class FilterProperties {

    private List<String> includeKeywords = new ArrayList<>();
    private List<String> excludeKeywords = new ArrayList<>();
    private List<String> roleKeywords = new ArrayList<>();

    public void setIncludeKeywords(List<String> includeKeywords) {
        this.includeKeywords = includeKeywords != null ? includeKeywords : new ArrayList<>();
    }

    public void setExcludeKeywords(List<String> excludeKeywords) {
        this.excludeKeywords = excludeKeywords != null ? excludeKeywords : new ArrayList<>();
    }

    public void setRoleKeywords(List<String> roleKeywords) {
        this.roleKeywords = roleKeywords != null ? roleKeywords : new ArrayList<>();
    }
}
