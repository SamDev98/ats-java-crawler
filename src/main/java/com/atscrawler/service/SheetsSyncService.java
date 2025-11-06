package com.atscrawler.service;

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

import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SheetsSyncService {
  private static final Logger log = LoggerFactory.getLogger(SheetsSyncService.class);
  private final JobRepository repo;
  @Value("${google.sheets.enabled:}") private boolean enabled;
  @Value("${google.sheets.spreadsheetId:}") private String spreadsheetId;
  @Value("${google.credentials.path:}") private String credentialsPath;
  private static final String TAB = "Jobs";
  private static final List<String> HEADER = List.of("Company","Title","Source","URL","First Seen","Last Seen","Active","Status","Notes");

  public SheetsSyncService(JobRepository repo){ this.repo = repo; }

  private Sheets client() throws Exception {
    GoogleCredentials creds = GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
        .createScoped(List.of("https://www.googleapis.com/auth/spreadsheets"));
    return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), new HttpCredentialsAdapter(creds))
        .setApplicationName("ATS Java Crawler").build();
  }

  public void pushJobsToSheet() {
    if (!enabled || spreadsheetId.isBlank()) { return; }
    try {
      Sheets sheets = client();
        sheets.spreadsheets().values().update(spreadsheetId, TAB + "!A1:I1",
                        new ValueRange().setValues(List.of(new ArrayList<>(HEADER))))
                .setValueInputOption("RAW").execute();
      var fmt = DateTimeFormatter.ISO_DATE;
      var all = repo.findAll();
        var rows = all.stream().map(j -> {
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
        }).collect(Collectors.toList());

      sheets.spreadsheets().values().clear(spreadsheetId, TAB+"!A2:I1000000", new ClearValuesRequest()).execute();
      if (!rows.isEmpty()) {
        sheets.spreadsheets().values().update(spreadsheetId, TAB+"!A2", new ValueRange().setValues(rows)).setValueInputOption("RAW").execute();
      }

      Integer sheetId = getSheetId(sheets);
      List<Request> fx = new ArrayList<>();
      fx.add(new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
          .setProperties(new SheetProperties().setSheetId(sheetId).setGridProperties(new GridProperties().setFrozenRowCount(1)))
          .setFields("gridProperties.frozenRowCount")));
      fx.add(new Request().setSetBasicFilter(new SetBasicFilterRequest()
          .setFilter(new BasicFilter().setRange(new GridRange().setSheetId(sheetId).setStartRowIndex(0).setStartColumnIndex(0).setEndColumnIndex(9)))));

      for (int col=0; col<9; col++) {
        int width = switch (col) { case 0,2 -> 150; case 1 -> 260; case 3 -> 420; default -> 120; };
        fx.add(new Request().setUpdateDimensionProperties(new UpdateDimensionPropertiesRequest()
            .setRange(new DimensionRange().setSheetId(sheetId).setDimension("COLUMNS").setStartIndex(col).setEndIndex(col+1))
            .setProperties(new DimensionProperties().setPixelSize(width)).setFields("pixelSize")));
      }

      var today = LocalDate.now();
      List<Integer> newRows = new ArrayList<>(), expiredRows = new ArrayList<>();
      for (int i=0;i<all.size();i++) {
        var j = all.get(i);
        if (j.getFirstSeen()!=null && j.getFirstSeen().isEqual(today)) newRows.add(i);
        if (!j.isActive()) expiredRows.add(i);
      }
      var green = new Color().setRed(0.85f).setGreen(0.97f).setBlue(0.88f);
      var red = new Color().setRed(0.98f).setGreen(0.85f).setBlue(0.85f);
      for (Integer i : newRows) fx.add(colorRow(sheetId, i, green));
      for (Integer i : expiredRows) fx.add(colorRow(sheetId, i, red));
      if (!fx.isEmpty()) sheets.spreadsheets().batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest().setRequests(fx)).execute();
    } catch (Exception ignored) {}
  }

  private Request colorRow(Integer sheetId, int idx, Color color) {
    return new Request().setRepeatCell(new RepeatCellRequest()
        .setRange(new GridRange().setSheetId(sheetId).setStartRowIndex(1+idx).setEndRowIndex(2+idx).setStartColumnIndex(0).setEndColumnIndex(9))
        .setCell(new CellData().setUserEnteredFormat(new CellFormat().setBackgroundColor(color)))
        .setFields("userEnteredFormat.backgroundColor"));
  }

  private Integer getSheetId(Sheets sheets) throws Exception {
    Spreadsheet ss = sheets.spreadsheets().get(spreadsheetId).execute();
    return ss.getSheets().stream().filter(s -> "Jobs".equals(s.getProperties().getTitle())).findFirst()
        .orElseThrow(() -> new IllegalStateException("Sheet tab 'Jobs' not found"))
        .getProperties().getSheetId();
  }

    public int pullStatusUpdatesToDb() {
        if (!enabled || spreadsheetId.isBlank()) return 0;
        try {
            Sheets sheets = client();
            ValueRange vr = sheets.spreadsheets().values().get(spreadsheetId, TAB + "!A2:I1000000").execute();
            if (vr.getValues() == null) return 0;

            int updated = 0;
            for (List<Object> row : vr.getValues()) {
                if (row.size() < 9) continue;

                String url = String.valueOf(row.get(3));
                String status = String.valueOf(row.get(7));
                String notes = String.valueOf(row.get(8));

                if (url.isBlank()) continue;
                var opt = repo.findByUrl(url);
                if (opt.isPresent()) {
                    var job = opt.get();
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

            log.info("🔁 {} job(s) updated from Google Sheets.", updated);
            return updated;

        } catch (Exception e) {
            log.error("❌ Failed to pull status updates from Google Sheets: {}", e.getMessage(), e);
            return 0;
        }
    }
}
