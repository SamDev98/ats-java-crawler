package com.atscrawler.service;

import com.atscrawler.model.Job;
import com.atscrawler.repository.JobRepository;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Production-grade Google Sheets sync service.
 * Manages sheet structure, formatting, and bidirectional sync.
 * Uses existing sheet (no auto-create) for maximum reliability.
 */
@Service
public class SheetsSyncService {
    private static final Logger log = LoggerFactory.getLogger(SheetsSyncService.class);
    private final JobRepository repo;

    @Value("${sheets.enabled:false}") private boolean enabled;
    @Value("${sheets.spreadsheetId:}") private String spreadsheetId;
    @Value("${google.credentials:}") private String credentialsPath;

    private static final String TAB = "Jobs";
    private static final List<String> HEADER = List.of(
            "Company", "Title", "Source", "URL", "First Seen", "Last Seen", "Active", "Status", "Notes"
    );

    public SheetsSyncService(JobRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.info("⚙️ Google Sheets integration disabled");
            return;
        }

        if (spreadsheetId.isBlank()) {
            log.error("❌ sheets.enabled=true but sheets.spreadsheetId is empty");
            log.error("   Setup instructions:");
            log.error("   1. Create sheet at https://sheets.google.com");
            log.error("   2. Share with: scrapper-job@scrapper-jobs-477123.iam.gserviceaccount.com (Editor)");
            log.error("   3. Copy spreadsheet ID from URL");
            log.error("   4. Set: sheets.spreadsheetId=YOUR_ID");
            this.enabled = false;
            return;
        }

        try {
            Sheets sheets = client();
            Spreadsheet ss = sheets.spreadsheets().get(spreadsheetId).execute();

            // Validate/create tab
            ensureTabExists(sheets, ss);

            // Validate/create header
            ensureHeaderExists(sheets);

            log.info("✅ Google Sheets initialized");
            log.info("   📊 Title: {}", ss.getProperties().getTitle());
            log.info("   🆔 ID: {}", spreadsheetId);
            log.info("   🔗 URL: https://docs.google.com/spreadsheets/d/{}/edit", spreadsheetId);

        } catch (Exception e) {
            log.error("❌ Failed to initialize Sheets: {}", e.getMessage());
            log.error("   Verify sheet exists and is shared with SA");
            this.enabled = false;
        }
    }

    /**
     * Ensures "Jobs" tab exists, creates if missing.
     */
    private void ensureTabExists(Sheets sheets, Spreadsheet ss) throws Exception {
        boolean tabExists = ss.getSheets().stream()
                .anyMatch(s -> TAB.equals(s.getProperties().getTitle()));

        if (!tabExists) {
            log.warn("⚠️  Tab '{}' not found, creating...", TAB);

            Request createTab = new Request()
                    .setAddSheet(new AddSheetRequest()
                            .setProperties(new SheetProperties()
                                    .setTitle(TAB)
                                    .setGridProperties(new GridProperties()
                                            .setFrozenRowCount(1)
                                            .setRowCount(10000)
                                            .setColumnCount(9))));

            sheets.spreadsheets()
                    .batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest()
                            .setRequests(List.of(createTab)))
                    .execute();

            log.info("✅ Tab '{}' created", TAB);
        }
    }

    /**
     * Ensures header row exists, creates if missing.
     */
    private void ensureHeaderExists(Sheets sheets) throws Exception {
        ValueRange vr = sheets.spreadsheets().values()
                .get(spreadsheetId, TAB + "!A1:I1")
                .execute();

        boolean headerMissing = vr.getValues() == null ||
                vr.getValues().isEmpty() ||
                vr.getValues().get(0).size() < 9;

        if (headerMissing) {
            log.warn("⚠️  Header missing, creating...");

            sheets.spreadsheets().values()
                    .update(spreadsheetId, TAB + "!A1:I1",
                            new ValueRange().setValues(List.of(new ArrayList<>(HEADER))))
                    .setValueInputOption("RAW")
                    .execute();

            // Format header (bold + gray background)
            Integer sheetId = getSheetId(sheets);
            Request formatHeader = new Request()
                    .setRepeatCell(new RepeatCellRequest()
                            .setRange(new GridRange()
                                    .setSheetId(sheetId)
                                    .setStartRowIndex(0)
                                    .setEndRowIndex(1))
                            .setCell(new CellData()
                                    .setUserEnteredFormat(new CellFormat()
                                            .setBackgroundColor(new Color()
                                                    .setRed(0.9f).setGreen(0.9f).setBlue(0.9f))
                                            .setTextFormat(new TextFormat()
                                                    .setBold(true)
                                                    .setFontSize(11))))
                            .setFields("userEnteredFormat(backgroundColor,textFormat)"));

            sheets.spreadsheets()
                    .batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest()
                            .setRequests(List.of(formatHeader)))
                    .execute();

            log.info("✅ Header created and formatted");
        }
    }

    private Sheets client() throws Exception {
        GoogleCredentials creds = GoogleCredentials
                .fromStream(new FileInputStream(credentialsPath))
                .createScoped(List.of("https://www.googleapis.com/auth/spreadsheets"));

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(creds)
        ).setApplicationName("ATS Java Crawler").build();
    }

    /**
     * Push all jobs from DB to Sheets.
     * Maintains full control over sheet structure and formatting.
     */
    public void pushJobsToSheet() {
        if (!enabled || spreadsheetId.isBlank()) return;

        try {
            Sheets sheets = client();

            // Clear existing data
            sheets.spreadsheets().values()
                    .clear(spreadsheetId, TAB + "!A2:I1000000", new ClearValuesRequest())
                    .execute();

            // ✅ MUDANÇA CRÍTICA: Sincronize APENAS vagas ATIVAS
            List<Job> activeJobs = repo.findByActiveTrue();  // ← Era repo.findAll()

            if (activeJobs.isEmpty()) {
                log.info("📊 No active jobs to sync");
                return;
            }

            // Write jobs
            DateTimeFormatter fmt = DateTimeFormatter.ISO_DATE;
            List<List<Object>> rows = activeJobs.stream().map(j -> {
                List<Object> r = new ArrayList<>();
                r.add(j.getCompany());
                r.add(j.getTitle());
                r.add(j.getSource());
                r.add(j.getUrl());
                r.add(j.getFirstSeen() != null ? fmt.format(j.getFirstSeen()) : "");
                r.add(j.getLastSeen() != null ? fmt.format(j.getLastSeen()) : "");
                r.add(j.isActive() ? "TRUE" : "FALSE");
                r.add(j.getStatus());
                r.add(Objects.toString(j.getNotes(), ""));
                return r;
            }).toList();

            sheets.spreadsheets().values()
                    .update(spreadsheetId, TAB + "!A2",
                            new ValueRange().setValues(rows))
                    .setValueInputOption("RAW")
                    .execute();

            // Apply formatting
            formatSheet(sheets, activeJobs);  // ← Passa activeJobs em vez de repo.findAll()

            log.info("✅ Synced {} active jobs to Google Sheets", activeJobs.size());

        } catch (Exception e) {
            log.error("❌ Failed to sync to Sheets: {}", e.getMessage());
        }
    }

    /**
     * Applies consistent formatting: freeze, filters, widths, colors.
     */
    private void formatSheet(Sheets sheets, List<Job> jobs) throws Exception {
        Integer sheetId = getSheetId(sheets);
        List<Request> requests = new ArrayList<>();

        // Freeze + filter + widths (mantém igual)
        requests.add(new Request()
                .setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                        .setProperties(new SheetProperties()
                                .setSheetId(sheetId)
                                .setGridProperties(new GridProperties().setFrozenRowCount(1)))
                        .setFields("gridProperties.frozenRowCount")));

        requests.add(new Request()
                .setSetBasicFilter(new SetBasicFilterRequest()
                        .setFilter(new BasicFilter()
                                .setRange(new GridRange()
                                        .setSheetId(sheetId)
                                        .setStartRowIndex(0)
                                        .setEndColumnIndex(9)))));

        int[] widths = {150, 260, 150, 420, 120, 120, 80, 120, 200};
        for (int i = 0; i < widths.length; i++) {
            requests.add(new Request()
                    .setUpdateDimensionProperties(new UpdateDimensionPropertiesRequest()
                            .setRange(new DimensionRange()
                                    .setSheetId(sheetId)
                                    .setDimension("COLUMNS")
                                    .setStartIndex(i)
                                    .setEndIndex(i + 1))
                            .setProperties(new DimensionProperties().setPixelSize(widths[i]))
                            .setFields("pixelSize")));
        }

        // ✅ Highlight usando a lista PASSADA (não repo.findAll())
        LocalDate today = LocalDate.now();
        Color green = new Color().setRed(0.85f).setGreen(0.97f).setBlue(0.88f);
        Color red = new Color().setRed(0.98f).setGreen(0.85f).setBlue(0.85f);

        for (int i = 0; i < jobs.size(); i++) {
            Job j = jobs.get(i);
            if (j.getFirstSeen() != null && j.getFirstSeen().isEqual(today)) {
                requests.add(colorRow(sheetId, i, green));
            }
            if (!j.isActive()) {
                requests.add(colorRow(sheetId, i, red));
            }
        }

        if (!requests.isEmpty()) {
            sheets.spreadsheets()
                    .batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest().setRequests(requests))
                    .execute();
        }
    }

    private Request colorRow(Integer sheetId, int idx, Color color) {
        return new Request()
                .setRepeatCell(new RepeatCellRequest()
                        .setRange(new GridRange()
                                .setSheetId(sheetId)
                                .setStartRowIndex(1 + idx)
                                .setEndRowIndex(2 + idx)
                                .setEndColumnIndex(9))
                        .setCell(new CellData()
                                .setUserEnteredFormat(new CellFormat().setBackgroundColor(color)))
                        .setFields("userEnteredFormat.backgroundColor"));
    }

    private Integer getSheetId(Sheets sheets) throws Exception {
        Spreadsheet ss = sheets.spreadsheets().get(spreadsheetId).execute();
        return ss.getSheets().stream()
                .filter(s -> TAB.equals(s.getProperties().getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tab 'Jobs' not found"))
                .getProperties()
                .getSheetId();
    }

    /**
     * Pull manual edits from Sheets back to DB.
     * Allows user to update Status/Notes in Sheets, which sync back to DB.
     */
    public int pullStatusUpdatesToDb() {
        if (!enabled || spreadsheetId.isBlank()) return 0;

        try {
            Sheets sheets = client();
            ValueRange vr = sheets.spreadsheets().values()
                    .get(spreadsheetId, TAB + "!A2:I1000000")
                    .execute();

            if (vr.getValues() == null) return 0;

            int updated = 0;
            for (List<Object> row : vr.getValues()) {
                if (row.size() < 9) continue;

                String url = String.valueOf(row.get(3));
                String status = String.valueOf(row.get(7));
                String notes = String.valueOf(row.get(8));

                if (url.isBlank()) continue;

                Optional<Job> opt = repo.findByUrl(url);
                if (opt.isPresent()) {
                    Job job = opt.get();
                    boolean changed = false;

                    if (!status.isBlank() && !status.equals(job.getStatus())) {
                        job.setStatus(status);
                        changed = true;
                    }
                    if (!Objects.equals(notes, Objects.toString(job.getNotes(), ""))) {
                        job.setNotes(notes);
                        changed = true;
                    }

                    if (changed) {
                        repo.save(job);
                        updated++;
                    }
                }
            }

            if (updated > 0) {
                log.info("🔁 Pulled {} updates from Sheets", updated);
            }
            return updated;

        } catch (Exception e) {
            log.error("❌ Failed to pull from Sheets: {}", e.getMessage());
            return 0;
        }
    }
}