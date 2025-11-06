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
        // Combina título + URL + notas (onde pode vir a localização)
        String text = (job.getTitle() + " " + job.getUrl() + " " +
                (job.getNotes() == null ? "" : job.getNotes())).toLowerCase();

        boolean include = props.getIncludeKeywords().isEmpty()
                || props.getIncludeKeywords().stream().anyMatch(text::contains);

        boolean role = props.getRoleKeywords().isEmpty()
                || props.getRoleKeywords().stream().anyMatch(text::contains);

        boolean exclude = props.getExcludeKeywords().stream().anyMatch(text::contains);

        boolean result = (include || role) && !exclude;

        if (!result) {
            log.debug("❌ Rejected: {} | {}", job.getTitle(), text);
        } else {
            log.debug("✅ Accepted: {} | {}", job.getTitle(), text);
        }

        return result;
    }
}
