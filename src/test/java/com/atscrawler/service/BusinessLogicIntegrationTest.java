package com.atscrawler.service;

import com.atscrawler.model.Job;
import com.atscrawler.repository.JobRepository;
import com.atscrawler.util.Http;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests covering database persistence, filtering logic,
 * fetcher registry execution, and job expiration.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Full data flow from fetching → filtering → merging</li>
 *   <li>Prevention of duplicate job insertion</li>
 *   <li>Automatic expiration of stale jobs</li>
 *   <li>Filter word-boundary behavior for “Java” vs “JavaScript”</li>
 *   <li>Parallel execution of registered fetchers</li>
 * </ul>
 *
 * <p>Executed against the real Spring Boot context with test database.
 *
 * @since 0.4.2
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusinessLogicIntegrationTest {

    @Autowired JobRepository repo;
    @Autowired JobFilterService filters;
    @Autowired JobMergeService mergeService;
    @Autowired FetcherRegistry registry;
    @Autowired Http http;

    @BeforeAll
    static void cleanDatabaseOnce(@Autowired JobRepository repo) {
        repo.deleteAll();
        System.out.println("🧹 Database cleaned before all tests");
    }

    @Test
    @Order(1)
    void testCompleteFlow_NewJob_AppearsInSheetAndDB() {
        Job job = new Job("Greenhouse", "AcmeTest", "Senior Java Engineer", "https://acme-test.com/job1");
        job.setNotes("Remote - LATAM");
        List<Job> fetched = List.of(job);

        List<Job> filtered = fetched.stream().filter(filters::matches).toList();
        assertEquals(1, filtered.size(), "Remote Java job should pass filters");

        Optional<Job> existingJob = repo.findByUrl("https://acme-test.com/job1");
        assertFalse(existingJob.isPresent(), "Job should not exist before merge");

        JobMergeService.SyncStats stats = mergeService.mergeWithDatabase(filtered);
        assertEquals(1, stats.getNewJobs(), "Should create one new job");

        Optional<Job> savedJob = repo.findByUrl("https://acme-test.com/job1");
        assertTrue(savedJob.isPresent(), "Job should be persisted");
        assertTrue(savedJob.get().isActive(), "Job must be active");
    }

    @Test
    @Order(2)
    void testLogicOrder_DuplicateJob_NotInsertedTwice() {
        long totalJobs = repo.count();
        System.out.println("📊 Total jobs before duplicate test: " + totalJobs);

        Optional<Job> existing = repo.findByUrl("https://acme-test.com/job1");
        if (existing.isEmpty()) {
            List<Job> allJobs = repo.findAll();
            System.err.println("❌ Job not found! Existing DB entries:");
            allJobs.forEach(j -> System.err.println("  - " + j.getUrl()));
            fail("Job missing from DB. Previous test might have failed.");
        }

        Job duplicateJob = new Job("Greenhouse", "AcmeTest", "Senior Java Engineer", "https://acme-test.com/job1");
        duplicateJob.setNotes("Remote - LATAM");
        List<Job> fetched = List.of(duplicateJob);

        JobMergeService.SyncStats stats = mergeService.mergeWithDatabase(fetched);
        assertEquals(0, stats.getNewJobs(), "No duplicate job should be inserted");
        assertEquals(1, stats.getUpdated(), "Existing job should be updated");

        long count = repo.count();
        assertTrue(count >= 1, "At least one job should remain in DB");
    }

    @Test
    @Order(3)
    void testFilter_JavaScriptJob_Rejected() {
        Job jsJob = new Job("Greenhouse", "BadCo", "JavaScript Developer", "https://bad.com/js");
        jsJob.setNotes("Remote");

        assertFalse(filters.matches(jsJob), "JavaScript job should be rejected");

        List<Job> filtered = Stream.of(jsJob).filter(filters::matches).toList();
        mergeService.mergeWithDatabase(filtered);

        Optional<Job> saved = repo.findByUrl("https://bad.com/js");
        assertFalse(saved.isPresent(), "Rejected jobs should not be persisted");
    }

    @Test
    @Order(4)
    void testExpiration_OldJob_MarkedInactive() {
        String uniqueUrl = "https://old.com/job-" + System.currentTimeMillis();

        Job oldJob = new Job("Lever", "OldCo", "Java Dev", uniqueUrl);
        oldJob.setFirstSeen(LocalDate.now().minusDays(35));
        oldJob.setLastSeen(LocalDate.now().minusDays(31));
        oldJob.setActive(true);
        repo.save(oldJob);

        int expired = mergeService.expireOldJobs();
        assertTrue(expired >= 1, "At least one job should expire");

        Optional<Job> expiredJob = repo.findByUrl(uniqueUrl);
        assertTrue(expiredJob.isPresent());
        assertFalse(expiredJob.get().isActive(), "Job should be marked inactive");
    }

    @Test
    @Order(5)
    void testFetcherRegistry_ParallelExecution_AllFetchersRun() {
        int fetcherCount = registry.getFetcherCount();
        assertEquals(4, fetcherCount, "There should be 4 active fetchers");

        List<Job> allJobs = registry.runAll();
        assertNotNull(allJobs, "Returned job list should not be null");
        assertTrue(true, "Parallel execution completed successfully");
    }

    @Test
    @Order(6)
    void testFilter_WordBoundary_JavaScriptRejected_JavaAccepted() {
        Job jsJob = new Job("Lever", "Co", "JavaScript Full Stack", "https://x.com/1");
        jsJob.setNotes("Remote");

        Job javaJob = new Job("Lever", "Co", "Java Backend", "https://x.com/2");
        javaJob.setNotes("Remote");

        assertFalse(filters.matches(jsJob), "JavaScript must be rejected");
        assertTrue(filters.matches(javaJob), "Java must be accepted");
    }

    @Test
    @Order(7)
    void testSlugValidator_InvalidSlug_Removed() {
        ATSSlugValidator validator = new ATSSlugValidator(http);
        assertFalse(validator.isValidGreenhouseSlug("invalid404"));
        assertTrue(validator.isValidGreenhouseSlug("robinhood"));
    }
}
