# PROCOMIC MIHON EXTENSION — ENGINEERING HANDOFF

**Project:** `mihon-extension-ar-procomic` (`eu.kanade.tachiyomi.extension.ar.procomic`)  
**Target Platform:** Tachiyomi / Mihon (Android)  
**Primary Language:** Kotlin (JVM 1.8 bytecode, compiled with JDK 21)  
**Authoring / Handoff Date:** August 2026  
**Status:** Alpha / Functional Prototype (Search REST + Listing RSC + Chapters REST + Page extraction active)

---

## 1. Project Overview

| Property | Value |
|---|---|
| **Project Name** | ProComic Mihon Extension |
| **Package Name** | `eu.kanade.tachiyomi.extension.ar.procomic` |
| **Version Name / Code** | `1.1` / `2` |
| **Primary Base URL** | `https://procomic.net` (mirrored from `procomic.pro`) |
| **Language** | Arabic (`ar`) catalog with mixed Arabic & English content |
| **Mihon Version Tested** | Mihon v0.16.5+ / Android 13 (Samsung Galaxy SM-A217F / arm64-v8a) |
| **Build System** | Gradle 8.14.4 + Android Gradle Plugin 8.7.0 + Kotlin 2.4.10 |
| **Dependencies** | `extensions-lib:6e0c96cea8` (compileOnly), `kotlinx-serialization-json:1.8.1` (compileOnly), `okhttp:5.0.0-alpha.11` (compileOnly), `jsoup:1.18.1` (compileOnly) |
| **Architectural Approach** | Hybrid: RSC Wire-Format extraction (Popular/Latest browse) + REST JSON APIs (Search & Chapter List pagination) |

---

## 2. Repository Structure & Source Responsibilities

```
ProComic Extension/
├── app/
│   ├── build.gradle.kts          # App-level build config (JVM 1.8 target, compileOnly dependencies, signing fallback)
│   └── src/main/
│       ├── AndroidManifest.xml   # Extension manifest with tachiyomi.extension feature and metadata declarations
│       ├── kotlin/eu/kanade/tachiyomi/extension/ar/procomic/
│       │   ├── ProComic.kt       # Core HttpSource implementation (Popular, Latest, Search, Details, Chapters, Pages)
│       │   ├── ProComicDto.kt    # Kotlinx Serialization data classes & custom StringOrListSerializer
│       │   ├── ProComicUtils.kt  # RSC bracket-counting JSON array/object extractor & text utilities
│       │   ├── ProComicFilters.kt# TypeFilter & GenreFilter definitions
│       │   └── ProComicDiag.kt   # Runtime diagnostic logger for Mihon logcat inspection
│       └── res/                  # Extension icon drawables (mipmap-hdpi to xxxhdpi)
├── docs/                         # Forensic audit reports, specs, investigation transcripts, handoff logs
├── gradle/wrapper/               # Gradle 8.14.4 wrapper binaries and properties
├── build.gradle.kts              # Root build script
├── settings.gradle.kts           # Gradle settings and repository resolution (JitPack, Google, MavenCentral)
└── gradle.properties             # Build environment properties
```

### Key Source Responsibilities
- **`ProComic.kt`:** Extends `HttpSource`. Routes search to `/api/public/series/search`, Popular/Latest to `/ar/series?sort=...` with `rscHeaders()`, chapters to `/api/chapters?contentId=...`, and parses chapter reader page lists.
- **`ProComicDto.kt`:** Contains serializable DTOs for RSC payloads and REST responses. Includes `StringOrListSerializer` to handle fields like `author` and `artist` that the server alternately returns as a string, string array, or null.
- **`ProComicUtils.kt`:** Implements bracket-counting boundary matching (`extractJsonArrayAfterKey`, `extractJsonObject`) to parse unescaped nested JSON from the streaming Next.js RSC wire format (`text/x-component`).
- **`ProComicDiag.kt`:** Structured logging utility outputting under the Android logcat tag `ProComicExt` for tracing request lifecycle stages.

---

## 3. Architecture & Data Flow

```
+-----------------------------------------------------------------------------------+
|                                  MIHON APP UI                                     |
+-----------------------------------------------------------------------------------+
       | (Browse Popular/Latest)         | (Search)                   | (Open Series)
       v                                 v                            v
  [ProComic.kt]                     [ProComic.kt]                [ProComic.kt]
  popularMangaRequest()             searchMangaRequest()         chapterListRequest()
       |                                 |                            |
       | RSC headers                     | Standard headers           | Standard headers
       v                                 v                            v
  GET /ar/series?sort=...           GET /api/public/             GET /api/chapters?
  &_rsc=pop1                        series/search?search=...     contentId={id}&page={n}
       |                                 |                            |
  (RSC text/x-component)           (application/json)           (application/json)
       |                                 |                            |
       v                                 v                            v
  [ProComicUtils.kt]                [ProComicDto.kt]             [ProComicDto.kt]
  extractJsonArrayAfterKey()        ProComicSearchResponse       ProComicChapterListResponse
       |                                 |                            |
       v                                 v                            v
  List<ProComicSeriesDto>           List<ProComicSeriesDto>      List<ProComicChapterDto>
       |                                 |                            |
       +----------------+----------------+                            |
                        |                                             |
                        v                                             v
                 toSManga() mapping                           toSChapter() mapping
                        |                                             |
                        v                                             v
                 MangasPage(mangas)                           List<SChapter>
                        |                                             |
                        +----------------+----------------------------+
                                         |
                                         v
                                  MIHON APP VIEWS
```

### Major Flows:
1. **Popular / Latest:** Fetches `/ar/series?sort=popular` or `latest` using RSC wire format headers (`RSC: 1`, `Next-Router-State-Tree: [...]`). `ProComicUtils.kt` extracts the `initialSeries` JSON array, mapped to `SManga` models. Client-side ordering ensures distinct views.
2. **Search:** Executes server-side query against `GET /api/public/series/search?status=approved&limit=18&page={page}&sort=latest&search={encoded_query}&type={type}`. Decodes `ProComicSearchResponse`, filters non-comics (`type != "novel"`), calculates `hasNextPage = meta.page < meta.pages`.
3. **Details:** Calls `mangaDetailsRequest` with `/ar/series/{type}/{id}/{slug}?_rsc=det`. Falls back gracefully to listing metadata if the detail RSC stream returns Next.js client redirect tokens.
4. **Chapters:** Queries REST endpoint `GET /api/chapters?contentId={seriesId}&page={page}` across all pages where `hasMore = true`. Maps all approved chapters (including Arabic and English translation releases) into `SChapter`.
5. **Page List / Reader:** Queries chapter URL `/ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNumber}?_rsc=rdr` with RSC headers. Extracts page image URLs from the `images` prop array.

---

## 4. Complete Investigation History

1. **Extension Loading Investigation:** Fixed manifest declarations (`tachiyomi.extension` feature) and JVM bytecode target (`JVM_1_8`) so Android D8 desugaring and Mihon classloading succeed.
2. **WebView vs Extension Divergence:** Confirmed that sending global RSC headers broke the in-app WebView because the server responded with streaming Flight components (`text/x-component`) instead of HTML. Resolved by isolating RSC headers to `rscHeaders()` on specific requests.
3. **Domain Migration:** Discovered `procomic.pro` redirects / proxies to `procomic.net`. Base URL updated to `https://procomic.net`.
4. **RSC Redirect Trap on Older Series:** Discovered series IDs `< ~686` returned HTTP 200 with `5:E{"digest":"NEXT_REDIRECT;replace;/ar/{slug}-{id};308;"}` when querying detail RSC routes.
5. **Chapter REST API Discovery:** Found `GET /api/chapters?contentId={id}` which bypasses RSC redirects, returns clean JSON across all series IDs, and supports pagination.
6. **Search Backend Discovery:** Puppeteer DevTools tracing intercepted browser network calls and proved the web UI does not search client-side, but hits `GET /api/public/series/search?search={query}`.

---

## 5. Evidence Classification

- **VERIFIED FACT:** The search backend is `GET /api/public/series/search` (validated with browser DevTools capture, live curl queries, and simulation).
- **VERIFIED FACT:** The chapter backend is `GET /api/chapters?contentId={id}&page={n}` (validated across multiple series IDs).
- **VERIFIED FACT:** Series with `type == "novel"` are text prose web novels and must be filtered out for Mihon comic reading.
- **HIGH-CONFIDENCE INFERENCE:** The RSC stream at `/ar/series` represents a fixed server-rendered cache of the ~18 newest series.
- **HIGH-CONFIDENCE INFERENCE:** Server-side genre filtering on the search REST endpoint is currently ignored by the backend (only `type` and `search` are filtered).
- **UNKNOWN:** CDN guest throttling limits (historical `publicImageCount: 3` token) and whether session cookies allow full unauthenticated chapter viewing on all future series.

---

## 6. Current Live Website Architecture & Endpoints

| Endpoint | Method | Params | Content-Type | Purpose |
|---|---|---|---|---|
| `/ar/series?sort={sort}&_rsc=pop1` | GET | `sort=popular\|latest`, `page=1` | `text/x-component` | Browse initial series list |
| `/api/public/series/search` | GET | `search={q}`, `limit=18`, `page={n}`, `sort=latest`, `status=approved`, `type={type}` | `application/json` | Server-side series search |
| `/api/chapters` | GET | `contentId={seriesId}`, `page={n}` | `application/json` | Chapter listing and pagination |
| `/ar/series/{type}/{id}/{slug}?_rsc=det` | GET | `_rsc=det` | `text/x-component` | Series detail RSC |
| `/ar/series/{type}/{id}/{slug}/{chId}/{chNum}?_rsc=rdr` | GET | `_rsc=rdr` | `text/x-component` | Chapter reader page images |

---

## 7. Search Backend Details

- **Endpoint:** `GET https://procomic.net/api/public/series/search`
- **Required Param:** `search` (cannot be empty; default `"a"` used for broad match).
- **Pagination Fields:**
  - `meta.page`: current page index (1-indexed)
  - `meta.pages`: total page count
  - `meta.limit`: items per page (default 18)
  - `meta.total`: total estimated items
- **Supported Filter:** `type=manga`, `type=manhwa`, `type=manhua`.

---

## 8. Feature Matrix

| Feature | Current Status | Evidence | Confidence | Known Limitation |
|---|---|---|---|---|
| **Popular** | Working | RSC listing extraction | VERIFIED FACT | Limited to server's 14 newest non-novel series |
| **Latest** | Working | RSC listing extraction + client sort | VERIFIED FACT | Limited to server's 14 newest non-novel series |
| **Search** | Working | REST `/api/public/series/search` | VERIFIED FACT | Full catalog search (hundreds of titles) |
| **Details** | Working | RSC stream + fallback to listing DTO | HIGH-CONFIDENCE | Older series return redirect tokens |
| **Chapters** | Working | REST `/api/chapters` pagination | VERIFIED FACT | All approved chapters shown (AR & EN) |
| **Reader** | Working | RSC `images` extraction | HIGH-CONFIDENCE | CDN image protection rules may apply |
| **WebView** | Working | Standard HTML headers | VERIFIED FACT | Clean browser navigation |

---

## 9. Completed Commits in this Project

1. `6e9555d` - *fix(manifest): add missing uses-feature tachiyomi.extension*
2. `254ba52` - *fix(headers): move RSC headers to per-request rscHeaders() only*
3. `a3d1141` - *debug(diag): add runtime instrumentation for RSC parse pipeline*
4. `9fd4381` - *fix(dto): handle author/artist as string or array in ProComicSeriesMetadata*
5. `e3f77f6` - *fix: domain migration + series detail + chapter list parsing*
6. `9a8b870` - *fix: client-side sort to differentiate Popular/Latest; hasNextPage=false*
7. `ee11a67` - *fix: chapter list via REST API — solves 0 chapters on all series*
8. `beabd4a` - *fix: search URL — _rsc= was inside fragment, poisoning query string*
9. `4023674` - *fix: chapter API response total field is null not Int*
10. `c0bc39a` - *feat(search): implement server-side search via /api/public/series/search*

---

## 10. Prior Invalid Assumptions & Lessons Learned

1. **Assumption:** Search is client-side only because RSC route `/ar/series?search=...` returned empty.  
   **Reality:** The website uses a dedicated REST endpoint `/api/public/series/search` completely separate from Next.js RSC routes.
2. **Assumption:** Catalogue only contained 14-18 series.  
   **Reality:** The `/ar/series` RSC stream only returns the 18 newest additions. The full database contains hundreds of entries accessible via the search REST API.
3. **Assumption:** All chapters on Arabic extension must be tagged `language: "AR"`.  
   **Reality:** ProComic hosts both Arabic and English translation releases under the same series entries. Filtering by `AR` caused 0 chapters on many series.
4. **Assumption:** All series details can be parsed from `/ar/series/{type}/{id}/{slug}` RSC.  
   **Reality:** Older series (id < 686) issue a Next.js `NEXT_REDIRECT` to slug-based URLs in their RSC stream.

---

## 11. Next Recommended Actions for Manus AI

### Primary Next Action:
1. **Device Smoke Test:** Install the latest APK (`app/build/outputs/apk/debug/app-debug.apk`), launch Mihon, perform search queries (`assassin`, `dragon`, `hunter`, `سيف`), and verify opening a chapter reader loads all pages seamlessly.

### Secondary Future Work:
2. **Explore Pagination for Popular/Latest:** Investigate whether an undocumented REST listing endpoint exists (e.g. `/api/public/series/popular` or `/api/public/series/latest`) to allow infinite scrolling on browse tabs.
3. **Genre Filter Integration:** Check if the backend adds tag/genre query filtering support to `/api/public/series/search`.
