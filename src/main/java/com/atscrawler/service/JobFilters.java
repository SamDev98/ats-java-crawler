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
        // Combina título + URL + notas (location vem aqui)
        String text = (job.getTitle() + " " + job.getUrl() + " " +
                (job.getNotes() == null ? "" : job.getNotes())).toLowerCase();

        // ✅ DEVE ter pelo menos 1 role keyword (java/spring/kotlin)
        boolean hasRole = props.getRoleKeywords().isEmpty()
                || props.getRoleKeywords().stream().anyMatch(text::contains);

        // ✅ Se include keywords estiver VAZIO → passa (assume remote)
        // Se tiver keywords → deve ter pelo menos 1
        boolean hasInclude = props.getIncludeKeywords().isEmpty()
                || props.getIncludeKeywords().stream().anyMatch(text::contains);

        // ❌ NÃO DEVE ter nenhum exclude keyword
        boolean hasExclude = props.getExcludeKeywords().stream().anyMatch(text::contains);

        // ✅ CORREÇÃO: Agora requer ROLE + opcionalmente INCLUDE, mas nunca EXCLUDE
        boolean result = hasRole && hasInclude && !hasExclude;

        // Debug: Log primeiras 10 rejeições de vagas Java
        if (hasRole && !result) {
            log.debug("❌ Java job rejected: {} | Include:{} Exclude:{}",
                    job.getTitle(), hasInclude, hasExclude);
        }

        return result;
    }
}