# Football AI Pro — Android MVP

A native Android (Kotlin + Jetpack Compose) football data analysis app.

## Current features
- API-Football fixture list for a selected date (Asia/Dhaka).
- Provider prediction endpoint display: 1X2 percentages, advice, goals and under/over field when supplied.
- First-five-minute card event lookup for a selected fixture.
- API key stored locally in Android SharedPreferences.
- Explicit handling of missing data and API errors.

## Important limitations
- This is an MVP, not a guaranteed betting system and not an autonomous AI model.
- The app does not fabricate BTTS, Double Chance, odds, referee stats, or first-five-minute event probabilities.
- First-five-minute card lookup checks historical/live event data for the selected fixture only. It does not yet scan thousands of historical fixtures to estimate a statistically robust probability.
- API-Football free tier is currently advertised as 100 requests/day. Requests are made manually to avoid exhausting quota. Confirm current limits and coverage in your provider dashboard.
- Do not share your API key or commit it to GitHub. The key is stored on-device in this MVP; a production public app should proxy requests through a secured backend to avoid key extraction.

## Create an API key
1. Register at https://www.api-football.com/
2. Activate the Free plan.
3. Copy the API key from your API-SPORTS dashboard.
4. Open the app → Settings → paste key → Save API Key.

## Build APK
Requirements: Android Studio (recent stable), JDK 17, Android SDK 35.

1. Open this folder in Android Studio.
2. Allow Gradle sync and install missing SDK components if prompted.
3. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Or from terminal:
```bash
gradle assembleDebug
```

## GitHub Actions
The workflow `.github/workflows/android.yml` builds a debug APK on push and lets you download it from the Actions run artifact.

## API endpoints used
- `GET /fixtures?date=YYYY-MM-DD&timezone=Asia/Dhaka`
- `GET /predictions?fixture=FIXTURE_ID`
- `GET /fixtures/events?fixture=FIXTURE_ID`

See https://www.api-football.com/documentation-v3

## Next development stage
Add cached historical match collection, last-five form, H2H, referee discipline data, calibrated first-5-minute card probabilities, BTTS and Double Chance only when supported by adequate data, prediction backtesting, and an encrypted backend proxy.
