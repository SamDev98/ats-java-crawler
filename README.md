# ATS Java Crawler v0.3.1 — Java Roles + Remote/LATAM Filters + 20 ATS

Production-grade Spring Boot app to collect **Java-related** remote jobs that are **remote anywhere** (not US-only) and accept **Brazil/LatAm**.

## What’s included
- ✅ 20 ATS (5 JSON: Greenhouse/Lever/Workable/Recruitee/Ashby + 15 HTML with Jsoup)
- ✅ Daily scheduler, merge/expire policy, Discord notifications
- ✅ Google Sheets UX: frozen header, auto filter, column widths, row highlight (NEW/EXPIRED)
- ✅ Configurable filters (env): `ROLE_KEYWORDS`, `INCLUDE_KEYWORDS`, `EXCLUDE_KEYWORDS`

## Quick start (local)
```bash
# edit application.properties to set DB_*, filters, and a few ATS slugs
mvn clean package -DskipTests
# set env vars in PowerShell (or create a .ps1 that sets them)
java -jar target/ats-java-crawler-0.3.1.jar
```

## CSV Import (add many companies fast)
- Provide a pipe-delimited CSV with header: `Company|URL|ATS|Timestamp`
- Set `COMPANY_CSV` env var (or `crawler.company-csv` in `application.properties`) to the file path.
- On startup, the app will parse the CSV, derive ATS slugs from the URL when possible, or fall back to a normalized company name, and merge them into the configured ATS lists.

Example (PowerShell):
```powershell
$env:COMPANY_CSV = "C:\\path\\to\\companies.csv"
mvn -q -DskipTests package
java -jar target/ats-java-crawler-0.4.0.jar
```

Notes:
- You can still set `ATS_*` env vars to seed or override slugs.
- The import is additive and de-duplicates case-insensitively.
- Best results when the `URL` column points to the ATS page (e.g., `boards.greenhouse.io/<slug>`); otherwise heuristics use the company name.

## Filters
- Must match **one** ROLE keyword (default: java/spring/spring boot/microservices)
- Must match **one** INCLUDE keyword (remote/latam/brazil/latin america/work from anywhere)
- Must match **none** of EXCLUDE (us only/onsite/etc.)

## Sheets
- Tab `Jobs` with columns: `Company | Title | Source | URL | First Seen | Last Seen | Active | Status | Notes`
- Freeze header, auto-filter, resize columns, NEW/EXPIRED highlight
- Edits to Status/Notes are pulled back into DB on next run
