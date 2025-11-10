# ATS Java Crawler v0.4.1

Spring Boot application that automatically crawls 4+ ATS platforms daily to find **remote Java/Spring/Kotlin jobs** accepting **LATAM/Brazil** candidates.

## Features

- ✅ **4 ATS Integrations**: Greenhouse, Lever, Recruitee, BreezyHR
- ✅ **Smart Filtering**: Word boundary regex (rejects "JavaScript", accepts "Java")
- ✅ **Google Sheets Sync**: Append-only bidirectional sync preserves manual edits
- ✅ **Non-Destructive**: Never deletes jobs, only marks as inactive after 30 days
- ✅ **GitHub Actions**: Automated daily runs at 07:00 UTC
- ✅ **Discord Notifications**: Daily summary with stats

## Quick Start

### Prerequisites
- Java 21
- PostgreSQL 16
- Google Cloud Service Account (for Sheets)

### Local Development
```bash
# 1. Configure environment
export DB_URL=jdbc:postgresql://localhost:5432/ats
export DB_USER=postgres
export DB_PASS=postgres
export GOOGLE_SHEETS_ID=your_sheet_id
export ATS_GREENHOUSE=robinhood,stripe,vercel

# 2. Build and run
mvn clean package -DskipTests
java -jar target/ats-java-crawler-0.4.1.jar
```

### GitHub Actions Setup
```bash
# 1. Configure secrets
gh secret set GOOGLE_CREDENTIALS < src/main/resources/credentials.json
gh secret set GOOGLE_SHEETS_ID --body "your_sheet_id"
gh secret set DISCORD_WEBHOOK --body "your_webhook_url"

# 2. Configure variables
gh variable set ATS_GREENHOUSE --body "company1,company2"
gh variable set ATS_LEVER --body "company3,company4"

# 3. Push workflows
git add .github/workflows/
git commit -m "ci: add GitHub Actions"
git push
```

## Configuration

### Filters (application.properties)
```properties
# Role keywords (word boundary regex)
filter.role-keywords=\\bjava\\b,\\bspring\\b,\\bkotlin\\b

# Exclude keywords
filter.exclude-keywords=javascript,frontend,us only,onsite

# Include keywords (optional - defaults to remote detection)
filter.include-keywords=
```

### ATS Companies
```properties
crawler.greenhouse-companies=${ATS_GREENHOUSE:}
crawler.lever-companies=${ATS_LEVER:}
crawler.recruitee-companies=${ATS_RECRUITEE:}
crawler.breezy-companies=${ATS_BREEZY:}
```

## Google Sheets Setup

1. Create spreadsheet at [sheets.google.com](https://sheets.google.com)
2. Share with Service Account email (Editor permission)
3. Copy Sheet ID from URL: `docs.google.com/spreadsheets/d/{SHEET_ID}/edit`
4. Set `GOOGLE_SHEETS_ID` secret

**Sheet columns:**
`Company | Title | Source | URL | First Seen | Last Seen | Active | Status | Notes`

## API Endpoints
```bash
# Manual sync trigger
curl http://localhost:8080/api/run-now

# Health check
curl http://localhost:8080/api/health

# Statistics
curl http://localhost:8080/api/stats
```

## Architecture
```
DailySync (Orchestrator)
├── FetcherRegistry (Parallel execution)
│   ├── GreenhouseFetcher
│   ├── LeverFetcher
│   ├── RecruiteeFetcher
│   └── BreezyFetcher
├── JobFilters (Word boundary validation)
├── JobMergeService (Non-destructive merge)
├── SheetsSyncService (Bidirectional sync)
└── DiscordNotifier
```

## Development
```bash
# Run tests
mvn test

# Run with dev profile
java -jar target/*.jar --spring.profiles.active=dev

# View logs
tail -f logs/app.log
```

## License

MIT