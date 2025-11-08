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
    public boolean matches(Job job) {
        String text = (job.getTitle() + " " + job.getUrl() + " " +
                (job.getNotes() == null ? "" : job.getNotes())).toLowerCase();

        // ✅ NÍVEL 1: OBRIGATÓRIO ter keyword Java
        boolean hasJava = props.getRoleKeywords().stream().anyMatch(text::contains);
        if (!hasJava) return false;

        // ✅ NÍVEL 2: OBRIGATÓRIO ser remote-friendly
        boolean hasRemote = props.getIncludeKeywords().isEmpty()
                ? text.matches(".*(remote|wfh|work from home|anywhere|latam|brazil).*")
                : props.getIncludeKeywords().stream().anyMatch(text::contains);
        if (!hasRemote) return false;

        // ✅ NÍVEL 3: BLOQUEAR excludes
        boolean hasExclude = props.getExcludeKeywords().stream()
                .anyMatch(text::contains);

        return !hasExclude;
    }
}