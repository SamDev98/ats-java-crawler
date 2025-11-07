package com.atscrawler;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import java.io.FileInputStream;
import java.io.File;
import java.util.List;

public class QuickTest {
    public static void main(String[] args) {
        String credPath = "src/main/resources/credentials.json";

        System.out.println("====================================");
        System.out.println("🔍 BILLING-ENABLED TEST");
        System.out.println("====================================\n");

        // 1. Validate file
        File credFile = new File(credPath);
        if (!credFile.exists()) {
            System.err.println("❌ credentials.json not found");
            return;
        }
        System.out.println("✅ File: " + credFile.getAbsolutePath());
        System.out.println("   Size: " + credFile.length() + " bytes");
        System.out.println("   Modified: " + new java.util.Date(credFile.lastModified()));
        System.out.println();

        try {
            // 2. Load credentials
            GoogleCredentials creds = GoogleCredentials
                    .fromStream(new FileInputStream(credPath))
                    .createScoped(List.of(
                            SheetsScopes.SPREADSHEETS,
                            SheetsScopes.DRIVE
                    ));

            if (creds instanceof ServiceAccountCredentials saCreds) {
                System.out.println("✅ Service Account loaded");
                System.out.println("   📧 Email: " + saCreds.getClientEmail());
                System.out.println("   🆔 Project: " + saCreds.getProjectId());
                System.out.println("   🔑 Key ID: " + saCreds.getPrivateKeyId());
                System.out.println();

                // 3. Check billing status (indirect - via quota)
                System.out.println("🔧 Testing API access...");
                Sheets service = new Sheets.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        new HttpCredentialsAdapter(creds)
                ).setApplicationName("BillingTest").build();

                System.out.println("✅ Sheets client initialized");
                System.out.println();

                // 4. Attempt create
                System.out.println("🔧 Attempting to create spreadsheet...");
                System.out.println("   (This will fail with 403 if billing not propagated)");
                System.out.println();

                Spreadsheet spreadsheet = new Spreadsheet()
                        .setProperties(new SpreadsheetProperties()
                                .setTitle("Billing Test - " + System.currentTimeMillis()));

                Spreadsheet result = service.spreadsheets().create(spreadsheet).execute();

                System.out.println("====================================");
                System.out.println("✅ SUCCESS! BILLING IS ACTIVE!");
                System.out.println("====================================");
                System.out.println("📊 Created: " + result.getSpreadsheetId());
                System.out.println("🔗 URL: https://docs.google.com/spreadsheets/d/" + result.getSpreadsheetId());
                System.out.println();
                System.out.println("🎉 Your app should work now!");
                System.out.println("   Run: mvn clean package && java -jar target/*.jar");

            } else {
                System.err.println("❌ Wrong credential type");
            }

        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            System.err.println();
            System.err.println("====================================");
            System.err.println("❌ API ERROR: " + e.getStatusCode());
            System.err.println("====================================");
            System.err.println("Message: " + e.getMessage());
            System.err.println();

            if (e.getStatusCode() == 403) {
                System.err.println("🔍 DIAGNOSIS: Still getting 403");
                System.err.println();
                System.err.println("Possible causes:");
                System.err.println("1. Billing not propagated yet (wait 10-15 min)");
                System.err.println("2. Billing on wrong project");
                System.err.println("3. Service Account created before billing");
                System.err.println("4. API quota exceeded (rare)");
                System.err.println();
                System.err.println("Solutions:");
                System.err.println("→ Wait 15 minutes and run again");
                System.err.println("→ Verify billing at: https://console.cloud.google.com/billing");
                System.err.println("→ Recreate Service Account + new credentials.json");
                System.err.println("→ OR use manual sheet setup (guaranteed to work)");
            } else if (e.getStatusCode() == 429) {
                System.err.println("🔍 DIAGNOSIS: Rate limit / Quota exceeded");
                System.err.println("   Wait 1 hour and try again");
            }

        } catch (Exception e) {
            System.err.println("❌ UNEXPECTED ERROR:");
            e.printStackTrace();
        }
    }
}