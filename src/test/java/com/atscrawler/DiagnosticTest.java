package com.atscrawler;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.AccessToken;

import java.io.FileInputStream;
import java.util.List;

public class DiagnosticTest {
    public static void main(String[] args) {
        String credPath = "src/main/resources/credentials.json";

        System.out.println("═══════════════════════════════════════");
        System.out.println("🔍 GOOGLE SHEETS 403 DIAGNOSTIC TOOL");
        System.out.println("═══════════════════════════════════════\n");

        try {
            // 1. Load credentials
            System.out.println("📄 Step 1: Loading credentials...");
            GoogleCredentials creds = GoogleCredentials
                    .fromStream(new FileInputStream(credPath))
                    .createScoped(List.of(
                            SheetsScopes.SPREADSHEETS,
                            SheetsScopes.DRIVE
                    ));

            // 2. Identify credential type
            System.out.println("✅ Credentials loaded\n");
            System.out.println("📋 Step 2: Credential Information");
            System.out.println("───────────────────────────────────────");

            if (creds instanceof ServiceAccountCredentials) {
                ServiceAccountCredentials saCreds = (ServiceAccountCredentials) creds;
                System.out.println("Type: SERVICE ACCOUNT ✅");
                System.out.println("Email: " + saCreds.getClientEmail());
                System.out.println("Project ID: " + saCreds.getProjectId());
                System.out.println("Private Key ID: " + saCreds.getPrivateKeyId());
                System.out.println("Scopes: " + List.of(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE));
            } else {
                System.out.println("Type: " + creds.getClass().getSimpleName());
                System.out.println("⚠️  WARNING: Not a Service Account!");
            }

            System.out.println();

            // 3. Get access token and show details
            System.out.println("🔑 Step 3: Obtaining Access Token");
            System.out.println("───────────────────────────────────────");
            creds.refresh();
            AccessToken token = creds.getAccessToken();

            if (token != null) {
                System.out.println("✅ Token obtained successfully");
                String tokenValue = token.getTokenValue();
                System.out.println("Token (first 50 chars): " +
                        tokenValue.substring(0, Math.min(50, tokenValue.length())) + "...");
                System.out.println("Expires at: " + token.getExpirationTime());
            } else {
                System.out.println("❌ Failed to obtain token");
            }

            System.out.println();

            // 4. Create Sheets client with custom interceptor
            System.out.println("🔧 Step 4: Building Sheets Client");
            System.out.println("───────────────────────────────────────");

            HttpRequestInitializer requestInitializer = new HttpRequestInitializer() {
                private final HttpCredentialsAdapter adapter = new HttpCredentialsAdapter(creds);

                @Override
                public void initialize(HttpRequest request) throws java.io.IOException {
                    adapter.initialize(request);

                    // Log authorization header
                    System.out.println("\n📤 REQUEST INFO:");
                    String authHeader = (String) request.getHeaders().getAuthorization();
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String tokenPreview = authHeader.substring(0, Math.min(60, authHeader.length())) + "...";
                        System.out.println("  Authorization: " + tokenPreview);
                    }
                }
            };

            Sheets service = new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    requestInitializer
            ).setApplicationName("DiagnosticTest").build();

            System.out.println("✅ Sheets client initialized\n");

            // 5. Attempt to create spreadsheet
            System.out.println("🚀 Step 5: Testing API Call");
            System.out.println("───────────────────────────────────────");
            System.out.println("Endpoint: POST https://sheets.googleapis.com/v4/spreadsheets");
            System.out.println("Action: Create test spreadsheet\n");

            Spreadsheet spreadsheet = new Spreadsheet()
                    .setProperties(new SpreadsheetProperties()
                            .setTitle("Diagnostic Test - " + System.currentTimeMillis()));

            System.out.println("Sending request...\n");

            Spreadsheet result = service.spreadsheets().create(spreadsheet).execute();

            System.out.println("═══════════════════════════════════════");
            System.out.println("✅ SUCCESS!");
            System.out.println("═══════════════════════════════════════");
            System.out.println("📊 Spreadsheet ID: " + result.getSpreadsheetId());
            System.out.println("🔗 URL: https://docs.google.com/spreadsheets/d/" + result.getSpreadsheetId());
            System.out.println("\n🎉 Your Service Account has correct permissions!");
            System.out.println("   The app should work now.");

        } catch (GoogleJsonResponseException e) {
            System.out.println("\n═══════════════════════════════════════");
            System.out.println("❌ API ERROR DETECTED");
            System.out.println("═══════════════════════════════════════");
            System.out.println("HTTP Status: " + e.getStatusCode());
            System.out.println("Error Message: " + e.getMessage());
            System.out.println();

            // Parse error details
            if (e.getDetails() != null && e.getDetails().getErrors() != null) {
                System.out.println("📋 Error Details:");
                e.getDetails().getErrors().forEach(error -> {
                    System.out.println("  Domain: " + error.getDomain());
                    System.out.println("  Reason: " + error.getReason());
                    System.out.println("  Message: " + error.getMessage());
                });
            }

            System.out.println();

            if (e.getStatusCode() == 403) {
                System.out.println("🔍 403 FORBIDDEN DIAGNOSIS");
                System.out.println("═══════════════════════════════════════");
                System.out.println();

                String errorMsg = e.getMessage().toLowerCase();

                if (errorMsg.contains("caller does not have permission")) {
                    System.out.println("Root Cause: SERVICE ACCOUNT LACKS PERMISSIONS\n");
                    System.out.println("Possible reasons:");
                    System.out.println("1. ❌ Billing not enabled on project");
                    System.out.println("2. ❌ Google Drive API not enabled");
                    System.out.println("3. ❌ Organization policy blocking SA");
                    System.out.println("4. ❌ Service Account has no IAM roles");
                    System.out.println();
                    System.out.println("Immediate Action Items:");
                    System.out.println();

                    // Extract project ID if SA
                    try {
                        GoogleCredentials tmpCreds = GoogleCredentials
                                .fromStream(new FileInputStream(credPath));
                        if (tmpCreds instanceof ServiceAccountCredentials) {
                            ServiceAccountCredentials saCreds = (ServiceAccountCredentials) tmpCreds;
                            String projectId = saCreds.getProjectId();

                            System.out.println("📍 Your Project ID: " + projectId);
                            System.out.println();
                            System.out.println("→ Check billing:");
                            System.out.println("  https://console.cloud.google.com/billing?project=" + projectId);
                            System.out.println();
                            System.out.println("→ Verify Drive API:");
                            System.out.println("  https://console.cloud.google.com/apis/library/drive.googleapis.com?project=" + projectId);
                            System.out.println();
                            System.out.println("→ Check IAM roles:");
                            System.out.println("  https://console.cloud.google.com/iam-admin/iam?project=" + projectId);
                            System.out.println("  (Add role 'Editor' to: " + saCreds.getClientEmail() + ")");
                        }
                    } catch (Exception ignored) {}

                    System.out.println();
                    System.out.println("🛠️  ALTERNATIVE: Use manual sheet setup");
                    System.out.println("   Set sheets.auto-create=false in application.properties");
                    System.out.println("   Create sheet manually and share with SA");
                }

                if (errorMsg.contains("quota") || errorMsg.contains("rate")) {
                    System.out.println("Root Cause: QUOTA/RATE LIMIT\n");
                    System.out.println("Solution: Wait 1 hour and retry");
                }
            }

        } catch (Exception e) {
            System.out.println("\n❌ UNEXPECTED ERROR:");
            System.out.println("═══════════════════════════════════════");
            e.printStackTrace();
            System.out.println();
            System.out.println("Possible causes:");
            System.out.println("→ Invalid credentials.json format");
            System.out.println("→ File permissions issue");
            System.out.println("→ Network/proxy blocking googleapis.com");
        }
    }
}