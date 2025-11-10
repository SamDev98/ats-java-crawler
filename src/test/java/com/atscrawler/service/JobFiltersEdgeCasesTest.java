package com.atscrawler.service;

import com.atscrawler.config.FilterProperties;
import com.atscrawler.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JobFilterService} covering corner cases and keyword precision.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Handling of null/empty fields</li>
 *   <li>Case-insensitive keyword matching</li>
 *   <li>Word boundary correctness (rejects "JavaScript")</li>
 *   <li>Remote detection and inclusion/exclusion logic</li>
 *   <li>Special and Unicode character handling</li>
 * </ul>
 *
 * @since 0.4.1
 */
@DisplayName("Edge Cases - Job Filters")
class JobFiltersEdgeCasesTest {

    private FilterProperties props;
    private JobFilterService filters;

    @BeforeEach
    void setup() {
        props = new FilterProperties();
        props.setRoleKeywords(List.of("java", "spring", "kotlin"));
        props.setIncludeKeywords(List.of());
        props.setExcludeKeywords(List.of("javascript", "frontend"));
        filters = new JobFilterService(props);
    }

    // ========================================
    // EMPTY/NULL HANDLING
    // ========================================

    @Test
    @DisplayName("❌ Null title rejected")
    void testFilter_NullTitle_Rejected() {
        Job job = new Job("Test", "Co", "Java Dev", "https://x.com");

        // Exception occurs in setter, not during filter evaluation
        assertThrows(IllegalArgumentException.class, () -> job.setTitle(null),
                "Setter should reject null titles");
    }

    @Test
    @DisplayName("✅ Null notes handled gracefully")
    void testFilter_NullNotes_HandledGracefully() {
        Job job = new Job("Test", "Co", "Java Developer", "https://x.com");
        job.setNotes(null);
        assertFalse(filters.matches(job));
    }

    @Test
    @DisplayName("❌ Empty role keyword list rejects all")
    void testFilter_EmptyRoleKeywords_RejectsAll() {
        props.setRoleKeywords(List.of());
        JobFilterService emptyFilter = new JobFilterService(props);

        Job job = new Job("Test", "Co", "Java Developer", "https://x.com");
        job.setNotes("Remote");

        assertFalse(emptyFilter.matches(job));
    }

    // ========================================
    // CASE SENSITIVITY
    // ========================================

    @Test
    @DisplayName("✅ Filter is case-insensitive")
    void testFilter_CaseInsensitive() {
        Job job1 = new Job("Test", "Co", "JAVA DEVELOPER", "https://x.com/1");
        job1.setNotes("REMOTE");

        Job job2 = new Job("Test", "Co", "java developer", "https://x.com/2");
        job2.setNotes("remote");

        assertTrue(filters.matches(job1));
        assertTrue(filters.matches(job2));
    }

    // ========================================
    // WORD BOUNDARY PRECISION
    // ========================================

    @Test
    @DisplayName("❌ 'javascript' rejected even with 'java' substring")
    void testFilter_JavaScript_Rejected() {
        Job job = new Job("Test", "Co", "JavaScript Full Stack", "https://x.com");
        job.setNotes("Remote");
        assertFalse(filters.matches(job));
    }

    @Test
    @DisplayName("✅ 'Java SE' accepted (word boundary)")
    void testFilter_JavaSE_Accepted() {
        Job job = new Job("Test", "Co", "Java SE Developer", "https://x.com");
        job.setNotes("Remote");
        assertTrue(filters.matches(job));
    }

    @Test
    @DisplayName("✅ 'Spring Boot' accepted")
    void testFilter_SpringBoot_Accepted() {
        Job job = new Job("Test", "Co", "Spring Boot Engineer", "https://x.com");
        job.setNotes("Remote - LATAM");
        assertTrue(filters.matches(job));
    }

    @Test
    @DisplayName("✅ 'Kotlin/JVM' accepted")
    void testFilter_KotlinJVM_Accepted() {
        Job job = new Job("Test", "Co", "Kotlin Developer", "https://x.com");
        job.setNotes("Remote");
        assertTrue(filters.matches(job));
    }

    // ========================================
    // EXCLUDE KEYWORDS
    // ========================================

    @Test
    @DisplayName("❌ Job with 'frontend' excluded")
    void testFilter_Frontend_Excluded() {
        Job job = new Job("Test", "Co", "Java Developer (Frontend Focus)", "https://x.com");
        job.setNotes("Remote");
        assertFalse(filters.matches(job));
    }

    @Test
    @DisplayName("❌ Job with 'US only' excluded")
    void testFilter_USOnly_Excluded() {
        props.setExcludeKeywords(List.of("us only", "usa only"));
        JobFilterService newFilter = new JobFilterService(props);

        Job job = new Job("Test", "Co", "Java Engineer", "https://x.com");
        job.setNotes("Remote - US only");

        assertFalse(newFilter.matches(job));
    }

    // ========================================
    // REMOTE DETECTION
    // ========================================

    @Test
    @DisplayName("✅ 'Remote' keyword detected")
    void testFilter_Remote_Detected() {
        Job job = new Job("Test", "Co", "Java Dev", "https://x.com");
        job.setNotes("Remote");
        assertTrue(filters.matches(job));
    }

    @Test
    @DisplayName("✅ 'Work from anywhere' detected")
    void testFilter_WorkFromAnywhere_Detected() {
        props.setIncludeKeywords(List.of("remote", "work from anywhere"));
        JobFilterService newFilter = new JobFilterService(props);

        Job job = new Job("Test", "Co", "Java Dev", "https://x.com");
        job.setNotes("Work from anywhere");
        assertTrue(newFilter.matches(job));
    }

    @Test
    @DisplayName("✅ 'LATAM' detected")
    void testFilter_LATAM_Detected() {
        props.setIncludeKeywords(List.of("latam", "latin america"));
        JobFilterService newFilter = new JobFilterService(props);

        Job job = new Job("Test", "Co", "Java Dev", "https://x.com");
        job.setNotes("Remote - LATAM");
        assertTrue(newFilter.matches(job));
    }

    @Test
    @DisplayName("❌ No remote keyword rejects job")
    void testFilter_NoRemote_Rejected() {
        Job job = new Job("Test", "Co", "Java Developer", "https://x.com");
        job.setNotes("San Francisco office");
        assertFalse(filters.matches(job));
    }

    // ========================================
    // SPECIAL CHARACTERS
    // ========================================

    @Test
    @DisplayName("✅ Special chars in title handled")
    void testFilter_SpecialChars_Handled() {
        Job job = new Job("Test", "Co", "Java/Kotlin (Spring)", "https://x.com");
        job.setNotes("Remote");
        assertTrue(filters.matches(job));
    }

    @Test
    @DisplayName("✅ Unicode chars handled")
    void testFilter_Unicode_Handled() {
        Job job = new Job("Test", "Co", "Java Developer 🚀", "https://x.com");
        job.setNotes("Remote 🌎");
        assertTrue(filters.matches(job));
    }
}
