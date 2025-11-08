package com.atscrawler.service;

import com.atscrawler.config.FilterProperties;
import com.atscrawler.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JobFilters {
    private static final Logger log = LoggerFactory.getLogger(JobFilters.class);
    private final FilterProperties props;

    public JobFilters(FilterProperties props) {
        this.props = props;
    }

    // JobFilters.java - REPLACE método matches()
// src/main/java/com/atscrawler/service/JobFilters.java
    public boolean matches(Job job) {
        String text = (job.getTitle() + " " + job.getUrl() + " " +
                (job.getNotes() == null ? "" : job.getNotes())).toLowerCase();

        // ✅ NÍVEL 1: Word boundary para "java" (evita "javascript")
        boolean hasJava = props.getRoleKeywords().stream()
                .anyMatch(kw -> text.matches(".*\\b" + kw + "\\b.*"));  // ← FIX AQUI

        if (!hasJava) return false;

        // NÍVEL 2: Remote-friendly (mantém igual)
        boolean hasRemote = props.getIncludeKeywords().isEmpty()
                ? text.matches(".*(remote|wfh|work from home|anywhere|latam|brazil).*")
                : props.getIncludeKeywords().stream().anyMatch(text::contains);

        if (!hasRemote) return false;

        // NÍVEL 3: Exclude keywords (mantém igual)
        boolean hasExclude = props.getExcludeKeywords().stream()
                .anyMatch(text::contains);

        return !hasExclude;
    }
}