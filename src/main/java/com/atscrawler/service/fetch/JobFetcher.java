package com.atscrawler.service.fetch;

import com.atscrawler.model.Job;
import java.util.List;

/**
 * Interface defining the contract for all job fetchers.
 *
 * <p>Each implementation of {@code JobFetcher} is responsible for fetching and parsing
 * job listings from a specific ATS or data source. The fetcher must identify itself
 * via {@link #getSourceName()} and return a list of {@link Job} objects.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link com.atscrawler.service.fetch.GreenhouseFetcher}</li>
 *   <li>{@link com.atscrawler.service.fetch.LeverFetcher}</li>
 *   <li>{@link com.atscrawler.service.fetch.BreezyFetcher}</li>
 * </ul>
 *
 * @author SamDev98
 * @since 0.4.1
 */
public interface JobFetcher {

    /**
     * Fetches job listings from a remote source.
     *
     * @return list of {@link Job} objects
     */
    List<Job> fetch();

    /**
     * Returns the human-readable name of the source (e.g., "Lever", "Greenhouse").
     *
     * @return source name
     */
    String getSourceName();
}
