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

    public boolean matches(Job job) {
        String text = (job.getTitle() + " " + job.getUrl() + " " +
                (job.getNotes() == null ? "" : job.getNotes())).toLowerCase();

        // ✅ FIX: Word boundary - rejeita "javascript"
        boolean hasJava = props.getRoleKeywords().stream()
                .anyMatch(kw -> text.matches(".*\\b" + kw.toLowerCase() + "\\b.*"));

        if (!hasJava) return false;

        // Remote check (mantém igual)
        boolean hasRemote = props.getIncludeKeywords().isEmpty()
                ? text.matches(".*(remote|wfh|work from home|anywhere|latam|brazil).*")
                : props.getIncludeKeywords().stream().anyMatch(text::contains);

        if (!hasRemote) return false;

        // Exclude check (mantém igual)
        boolean hasExclude = props.getExcludeKeywords().stream()
                .anyMatch(text::contains);

        return !hasExclude;
    }
}