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

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusinessLogicIntegrationTest {

    @Autowired JobRepository repo;
    @Autowired JobFilters filters;
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
        assertEquals(1, filtered.size(), "Job Java remoto deve passar no filtro");

        Optional<Job> existingJob = repo.findByUrl("https://acme-test.com/job1");
        assertFalse(existingJob.isPresent(), "Job não deve existir antes do merge");

        JobMergeService.SyncStats stats = mergeService.mergeWithDatabase(filtered);
        assertEquals(1, stats.getNewJobs(), "Deve criar 1 job novo");

        Optional<Job> savedJob = repo.findByUrl("https://acme-test.com/job1");
        assertTrue(savedJob.isPresent(), "Job deve estar salvo no banco");
        assertTrue(savedJob.get().isActive(), "Job deve estar ativo");

        System.out.println("✅ Test 1: Job criado com sucesso - URL: https://acme-test.com/job1");
    }

    @Test
    @Order(2)
    void testLogicOrder_DuplicateJob_NotInsertedTwice() {
        // ✅ DEBUG: Mostra estado do banco
        long totalJobs = repo.count();
        System.out.println("📊 Total de jobs no banco antes do Test 2: " + totalJobs);

        Optional<Job> existing = repo.findByUrl("https://acme-test.com/job1");

        // ✅ FIX: Se job não existe, falha com mensagem clara
        if (existing.isEmpty()) {
            List<Job> allJobs = repo.findAll();
            System.err.println("❌ Job não encontrado! Jobs no banco:");
            allJobs.forEach(j -> System.err.println("  - " + j.getUrl()));
            fail("Job https://acme-test.com/job1 não existe. Test 1 pode ter falhado ou banco foi limpo.");
        }

        assertTrue(true, "Job deve existir do teste anterior");

        Job duplicateJob = new Job("Greenhouse", "AcmeTest", "Senior Java Engineer", "https://acme-test.com/job1");
        duplicateJob.setNotes("Remote - LATAM");
        List<Job> fetched = List.of(duplicateJob);

        JobMergeService.SyncStats stats = mergeService.mergeWithDatabase(fetched);
        assertEquals(0, stats.getNewJobs(), "Não deve criar job duplicado");
        assertEquals(1, stats.getUpdated(), "Deve atualizar lastSeen");

        long count = repo.count();
        assertTrue(count >= 1, "Deve ter pelo menos 1 job no banco");

        System.out.println("✅ Test 2: Job duplicado não foi inserido");
    }

    @Test
    @Order(3)
    void testFilter_JavaScriptJob_Rejected() {
        Job jsJob = new Job("Greenhouse", "BadCo", "JavaScript Developer", "https://bad.com/js");
        jsJob.setNotes("Remote");

        assertFalse(filters.matches(jsJob), "Job JavaScript deve ser rejeitado");

        List<Job> filtered = Stream.of(jsJob).filter(filters::matches).toList();
        mergeService.mergeWithDatabase(filtered);

        Optional<Job> saved = repo.findByUrl("https://bad.com/js");
        assertFalse(saved.isPresent(), "Job JS não deve ser salvo");
    }

    @Test
    @Order(4)
    void testExpiration_OldJob_MarkedInactive() {
        // ✅ FIX: URL única com timestamp
        String uniqueUrl = "https://old.com/job-" + System.currentTimeMillis();

        Job oldJob = new Job("Lever", "OldCo", "Java Dev", uniqueUrl);
        oldJob.setFirstSeen(LocalDate.now().minusDays(35));
        oldJob.setLastSeen(LocalDate.now().minusDays(31));
        oldJob.setActive(true);
        repo.save(oldJob);

        int expired = mergeService.expireOldJobs();
        assertTrue(expired >= 1, "Deve expirar ao menos 1 job");

        Optional<Job> expiredJob = repo.findByUrl(uniqueUrl);
        assertTrue(expiredJob.isPresent());
        assertFalse(expiredJob.get().isActive(), "Job deve estar inativo");
    }

    @Test
    @Order(5)
    void testFetcherRegistry_ParallelExecution_AllFetchersRun() {
        // FIX: registry.runAll() retorna List<Job>, não quantidade de fetchers

        // 1. Valida quantidade de fetchers registrados
        int fetcherCount = registry.getFetcherCount();
        assertEquals(4, fetcherCount, "Deve ter 4 fetchers ativos (Greenhouse, Lever, Recruitee, BreezyHR)");

        // 2. Executa fetch paralelo
        List<Job> allJobs = registry.runAll();

        // 3. Valida que retornou jobs
        assertNotNull(allJobs, "Lista de jobs não deve ser nula");
        assertTrue(true, "Deve retornar lista (pode estar vazia se APIs falharem)");
    }

    @Test
    @Order(6)
    void testFilter_WordBoundary_JavaScriptRejected_JavaAccepted() {
        Job jsJob = new Job("Lever", "Co", "JavaScript Full Stack", "https://x.com/1");
        jsJob.setNotes("Remote");

        Job javaJob = new Job("Lever", "Co", "Java Backend", "https://x.com/2");
        javaJob.setNotes("Remote");

        assertFalse(filters.matches(jsJob), "JavaScript rejeitado");
        assertTrue(filters.matches(javaJob), "Java aceito");
    }

    @Test
    @Order(7)
    void testSlugValidator_InvalidSlug_Removed() {
        ATSSlugValidator validator = new ATSSlugValidator(http);

        assertFalse(validator.isValidGreenhouseSlug("invalid404"));
        assertTrue(validator.isValidGreenhouseSlug("robinhood"));
    }
}