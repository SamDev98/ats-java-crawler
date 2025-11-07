package com.atscrawler;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.util.List;

public class TestGoogleAuth {
    public static void main(String[] args) throws Exception {
        String credPath = "./src/main/resources/credentials.json";

        GoogleCredentials creds = GoogleCredentials
                .fromStream(new FileInputStream(credPath))
                .createScoped(List.of(
                        "https://www.googleapis.com/auth/spreadsheets",
                        "https://www.googleapis.com/auth/drive.file"
                ));

        Sheets sheets = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(creds)
        ).setApplicationName("Test").build();

        // Tenta criar sheet
        Spreadsheet ss = new Spreadsheet()
                .setProperties(new SpreadsheetProperties().setTitle("Test Sheet"));

        Spreadsheet result = sheets.spreadsheets().create(ss).execute();

        System.out.println("✅ SUCCESS! Created: " + result.getSpreadsheetId());
        System.out.println("🔗 https://docs.google.com/spreadsheets/d/" + result.getSpreadsheetId());
    }
}
