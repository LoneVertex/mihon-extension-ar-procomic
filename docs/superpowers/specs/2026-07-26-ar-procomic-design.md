# ar.procomic Extension Design Spec
**Date:** 2026-07-26  
**Status:** Approved (autonomously, per prompt directive)

---

## Architecture

A standalone Kotlin/Android Tachiyomi extension for `procomic.pro`, extending `HttpSource` directly (no multisrc template applies). The extension fetches data by making RSC (React Server Component) requests to the Next.js frontend with the `RSC: 1` header, then parses the RSC wire-format response to extract embedded manga data.

**Approach chosen:** RSC stream parsing (not HTML scraping, not REST API)
- RSC endpoints return structured JSON-like data without JavaScript execution
- A mobile User-Agent + `RSC: 1` header bypasses the WAF and returns HTTP 200
- No WebView interceptor required for browse/search/detail — only needed if chapter images turn out to require session cookies

---

## Stage 4 — Root Cause and Hypothesis Table

| Hypothesis | Evidence For | Evidence Against | Confidence |
|---|---|---|---|
| Site uses Cloudflare Turnstile (full JS challenge) | Prior automated fetch failures | curl + mobile UA gets HTTP 200; headers show cf-cache-status: DYNAMIC not challenge page | Low — DISPROVED |
| Site is a custom Next.js RSC app (not Madara/WPManga) | /_next/static/chunks/, (prochan-app) in bundle, RSC content-type header, no /wp-json/ | — | High — CONFIRMED |
| RSC stream contains all needed data (series, chapters, genres) | Direct curl of /ar/series?_rsc=1 returns series list; detail RSC returns chapter list with ids | — | High — CONFIRMED |
| Thumbnails (covers) publicly accessible | app.procomic.pro/series-cards/... returns HTTP 200 without auth | — | High — CONFIRMED |
| Chapter page images (CDN) require auth/session | cdn2.procomic.pro/... returns HTTP 403 with all tested headers/cookies | — | High — CONFIRMED |
| Only first N pages accessible to guests (publicImageCount=3) | RSC metadata: "publicImageCount":3 in protectionV2 | Only 3 CDN URLs in images array for guest RSC response | High — CONFIRMED |
| Login not required to browse/list/detail | All browsing RSC requests succeed without auth cookies | — | High — CONFIRMED |
| Content is SFW-filtered for guests | JS code: Guests can never disable safe browsing | — | High — CONFIRMED |

**Root cause of chapter page access limitation:** The site intentionally gates chapter pages beyond publicImageCount (currently 3) behind a login/account. The CDN (cdn2.procomic.pro) uses nginx-level hotlink/auth protection independent of Cloudflare. No cookie or header bypass succeeds without a valid authenticated session.

**Architecture decision:** Build for guest-mode operation (browse + 3 preview pages per chapter). WebView-based login flow flagged as a known limitation.

---

## Module Structure

```
src/ar/procomic/
├── build.gradle
├── AndroidManifest.xml          # lang=ar, nsfw=false, name=ProComic
├── res/
│   └── mipmap-*/ic_launcher.png
└── src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/
    ├── ProComic.kt              # Main HttpSource class
    ├── ProComicDto.kt           # Data transfer objects (Kotlinx serialization)
    ├── ProComicFilters.kt       # Filter classes (type, genre, status)
    └── ProComicUtils.kt         # RSC response parser utility
```

---

## Data Model (from recon evidence)

### Series (SManga mapping)
- id → url (as /series/{type}/{id}/{slug})
- title → title
- thumbnail (app.procomic.pro/series-cards/{id}/originals/...) → thumbnail_url
- type (manga/manhwa/manhua) → genre
- status → status
- genres[].en → genre (comma-joined)
- description → description (from detail RSC)

### Chapter (SChapter mapping)
- id → url (as /chapters/{id})
- chapter_number → chapter_number
- title → name
- Filter language == "AR" only
- translator → scanlator

### Pages
- images[] from RSC → Page list (limited to publicImageCount = 3 for guests)

---

## RSC Request Headers
- User-Agent: Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36
- RSC: 1
- Accept-Language: ar,en;q=0.9
- Next-Router-State-Tree: %5B%22%22%2C%7B%7D%5D

## Endpoint URLs
- Latest: GET /ar/series?_rsc=1
- Popular: GET /ar/series?sort=popular&_rsc=1 (to be verified)
- Search: GET /ar/series?search={query}&_rsc=1 (to be verified)
- Series detail: GET /ar/series/{type}/{id}/{slug}?_rsc=1
- Chapter list: Embedded in series detail RSC
- Page list: GET /ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNum}?_rsc=1

---

## Known Limitations

1. Chapter pages limited to 3 per chapter for guest users (enforced by CDN)
2. Search URL parameters unverified
3. Popular sort parameter unverified
4. Novel type excluded (Tachiyomi cannot render prose content)

---

## Definition of Done

- Popular/Latest return non-empty, correctly-parsed results
- Search returns relevant results
- Manga details (title, cover, description, genres) populated
- Chapter list shows at least 1 real chapter
- Page list returns 3 pages for a chapter
- Builds via gradlew assembleDebug
- No untested preferences, no NSFW toggle
