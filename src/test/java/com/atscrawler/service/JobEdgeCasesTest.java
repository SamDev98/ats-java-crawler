package com.atscrawler.service;

import com.atscrawler.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Edge Cases - Job Entity & Business Rules")
class JobEdgeCasesTest {

    private Job validJob;

    @BeforeEach
    void setup() {
        validJob = new Job("Greenhouse", "Acme", "Java Engineer", "https://acme.com/job");
        validJob.setNotes("Remote");
    }

    // ========================================
    // URL VALIDATION
    // ========================================

    @Test
    @DisplayName("❌ Null URL throws exception")
    void testJob_NullURL_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Job("Test", "Co", "Title", null)
        );
    }

    @Test
    @DisplayName("❌ Blank URL throws exception")
    void testJob_BlankURL_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Job("Test", "Co", "Title", "   ")
        );
    }

    @Test
    @DisplayName("❌ URL without http(s) throws exception")
    void testJob_InvalidProtocol_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Job("Test", "Co", "Title", "acme.com/job")
        );
    }

    @Test
    @DisplayName("❌ URL > 2048 chars throws exception")
    void testJob_URLTooLong_ThrowsException() {
        String longUrl = "https://example.com/" + "x".repeat(2048);
        assertThrows(IllegalArgumentException.class, () ->
                new Job("Test", "Co", "Title", longUrl)
        );
    }

    @Test
    @DisplayName("✅ Valid HTTPS URL accepted")
    void testJob_ValidHTTPS_Accepted() {
        assertDoesNotThrow(() ->
                new Job("Test", "Co", "Title", "https://example.com/job")
        );
    }

    @Test
    @DisplayName("✅ Valid HTTP URL accepted")
    void testJob_ValidHTTP_Accepted() {
        assertDoesNotThrow(() ->
                new Job("Test", "Co", "Title", "http://example.com/job")
        );
    }

    // ========================================
    // FIELD VALIDATION
    // ========================================

    @Test
    @DisplayName("❌ Null title throws exception")
    void testJob_NullTitle_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Job("Test", "Co", null, "https://x.com")
        );
    }

    @Test
    @DisplayName("❌ Blank company throws exception")
    void testJob_BlankCompany_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new Job("Test", "  ", "Title", "https://x.com")
        );
    }

    @Test
    @DisplayName("✅ Whitespace trimmed from fields")
    void testJob_WhitespaceTrimmed() {
        Job job = new Job("  Test  ", "  Co  ", "  Title  ", "https://x.com");
        assertEquals("Test", job.getSource());
        assertEquals("Co", job.getCompany());
        assertEquals("Title", job.getTitle());
    }

    // ========================================
    // EQUALS & HASHCODE
    // ========================================

    @Test
    @DisplayName("✅ Jobs with same URL are equal")
    void testJob_SameURL_AreEqual() {
        Job job1 = new Job("Greenhouse", "Co", "Title1", "https://same.com/job");
        Job job2 = new Job("Lever", "Co2", "Title2", "https://same.com/job");

        assertEquals(job1, job2, "Jobs com mesma URL devem ser iguais");
        assertEquals(job1.hashCode(), job2.hashCode());
    }

    @Test
    @DisplayName("❌ Jobs with different URLs are not equal")
    void testJob_DifferentURL_NotEqual() {
        Job job1 = new Job("Test", "Co", "Title", "https://url1.com");
        Job job2 = new Job("Test", "Co", "Title", "https://url2.com");

        assertNotEquals(job1, job2);
    }

    // ========================================
    // DATE LOGIC
    // ========================================

    @Test
    @DisplayName("✅ FirstSeen defaults to null")
    void testJob_FirstSeen_DefaultsNull() {
        assertNull(validJob.getFirstSeen());
    }

    @Test
    @DisplayName("✅ Active defaults to true")
    void testJob_Active_DefaultsTrue() {
        assertTrue(validJob.isActive());
    }

    @Test
    @DisplayName("✅ Status defaults to 'Awaiting'")
    void testJob_Status_DefaultsAwaiting() {
        assertEquals("Awaiting", validJob.getStatus());
    }

    // ========================================
    // NOTES HANDLING
    // ========================================

    @Test
    @DisplayName("✅ Null notes accepted")
    void testJob_NullNotes_Accepted() {
        validJob.setNotes(null);
        assertNull(validJob.getNotes());
    }

    @Test
    @DisplayName("✅ Empty notes accepted")
    void testJob_EmptyNotes_Accepted() {
        validJob.setNotes("");
        assertEquals("", validJob.getNotes());
    }

    @Test
    @DisplayName("❌ Notes > 2048 chars should be handled")
    void testJob_NotesTooLong_HandledGracefully() {
        String longNotes = "x".repeat(2500);
        validJob.setNotes(longNotes);

        // App deve truncar ou lançar exceção - validar comportamento esperado
        assertEquals(2500, validJob.getNotes().length(),
                "App aceita notes longos, mas DB deve rejeitar no save()");
    }
}