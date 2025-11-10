package com.atscrawler.service.fetch;

import com.atscrawler.config.CrawlerProperties;
import com.atscrawler.model.Job;
import com.atscrawler.util.Http;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@DisplayName("Unit Tests - ATS Fetchers")
class ATSFetchersUnitTest {

    @Mock private Http http;
    private CrawlerProperties props;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        props = new CrawlerProperties();
    }

    // ========================================
    // GREENHOUSE
    // ========================================

    @Test
    @DisplayName("✅ Greenhouse - Valid JSON returns jobs")
    void testGreenhouse_ValidJSON_ReturnsJobs() {
        String validJson = """
            {
                "jobs": [
                    {
                        "title": "Backend Engineer - Java",
                        "absolute_url": "https://boards.greenhouse.io/robinhood/jobs/123",
                        "location": { "name": "Remote" }
                    }
                ]
            }
            """;

        when(http.get(anyString())).thenReturn(validJson);

        props.setGreenhouseCompanies(List.of("robinhood"));
        GreenhouseFetcher fetcher = new GreenhouseFetcher(props, http);

        List<Job> jobs = fetcher.fetch();

        assertEquals(1, jobs.size());
        assertEquals("Backend Engineer - Java", jobs.getFirst().getTitle());
    }

    @Test
    @DisplayName("❌ Greenhouse - Empty companies list returns empty")
    void testGreenhouse_EmptyCompanies_ReturnsEmpty() {
        props.setGreenhouseCompanies(List.of());
        GreenhouseFetcher fetcher = new GreenhouseFetcher(props, http);

        List<Job> jobs = fetcher.fetch();

        assertTrue(jobs.isEmpty());
    }

    @Test
    @DisplayName("❌ Greenhouse - Null companies list returns empty")
    void testGreenhouse_NullCompanies_ReturnsEmpty() {
        props.setGreenhouseCompanies(null);
        GreenhouseFetcher fetcher = new GreenhouseFetcher(props, http);

        List<Job> jobs = fetcher.fetch();

        assertTrue(jobs.isEmpty());
    }

    @Test
    @DisplayName("❌ Greenhouse - HTML response skipped")
    void testGreenhouse_HTMLResponse_Skipped() {
        when(http.get(anyString())).thenReturn("<html>Not Found</html>");

        props.setGreenhouseCompanies(List.of("invalid"));
        GreenhouseFetcher fetcher = new GreenhouseFetcher(props, http);

        List<Job> jobs = fetcher.fetch();

        assertTrue(jobs.isEmpty());
    }

    // ========================================
    // LEVER
    // ========================================

    @Test
    @DisplayName("✅ Lever - Valid JSON returns jobs")
    void testLever_ValidJSON_ReturnsJobs() {
        String validJson = """
            [
                {
                    "text": "Senior Backend Engineer",
                    "hostedUrl": "https://jobs.lever.co/neon/abc123",
                    "categories": { "location": "Remote - Americas" }
                }
            ]
            """;

        when(http.get(anyString())).thenReturn(validJson);

        props.setLeverCompanies(List.of("neon"));
        LeverFetcher fetcher = new LeverFetcher(props, http);

        List<Job> jobs = fetcher.fetch();

        assertEquals(1, jobs.size());
        assertEquals("Senior Backend Engineer", jobs.getFirst().getTitle());
    }

    @Test
    @DisplayName("❌ Lever - 404 error returns empty")
    void testLever_404Error_ReturnsEmpty() {
        String errorJson = "{\"ok\":false,\"error\":\"Document not found\"}";
        when(http.get(anyString())).thenReturn(errorJson);

        props.setLeverCompanies(List.of("invalid"));
        LeverFetcher fetcher = new LeverFetcher(props, http);

        List<Job> jobs = fetcher.fetch();

        assertTrue(jobs.isEmpty());
    }

    // ========================================
    // RECRUITEE
    // ========================================

    @Test
    @DisplayName("✅ Recruitee - Valid JSON returns jobs")
    void testRecruitee_ValidJSON_ReturnsJobs() {
        String validJson = """
            {
                "offers": [
                    {
                        "title": "Java Developer",
                        "careers_url": "https://timedoctor.recruitee.com/o/123",
                        "locations": "[{\\"name\\":\\"Remote\\"}]"
                    }
                ]
            }
            """;

        when(http.get(anyString())).thenReturn(validJson);

        props.setRecruiteeCompanies(List.of("timedoctor"));
        RecruiteeFetcher fetcher = new RecruiteeFetcher(props, http);

        List<Job> jobs = fetcher.fetch();

        assertEquals(1, jobs.size());
        assertEquals("Java Developer", jobs.getFirst().getTitle());
    }

    // ========================================
    // BREEZYHR
    // ========================================

    @Test
    @DisplayName("✅ BreezyHR - Valid HTML returns jobs")
    void testBreezy_ValidHTML_ReturnsJobs() {
        String html = """
            <!DOCTYPE html>
            <html>
            <body>
                <a href="https://zapier.breezy.hr/p/123" class="position">
                    <h2>Backend Engineer - Java</h2>
                </a>
            </body>
            </html>
            """;

        when(http.get(anyString())).thenReturn(html);

        props.setBreezyCompanies(List.of("zapier"));
        BreezyFetcher fetcher = new BreezyFetcher(props, http);

        List<Job> jobs = fetcher.fetch();

        assertEquals(1, jobs.size());
        assertEquals("Backend Engineer - Java", jobs.getFirst().getTitle());
    }

    @Test
    @DisplayName("❌ BreezyHR - Empty HTML returns empty")
    void testBreezy_EmptyHTML_ReturnsEmpty() {
        when(http.get(anyString())).thenReturn("<html><body></body></html>");

        props.setBreezyCompanies(List.of("test"));
        BreezyFetcher fetcher = new BreezyFetcher(props, http);

        List<Job> jobs = fetcher.fetch();

        assertTrue(jobs.isEmpty());
    }
}