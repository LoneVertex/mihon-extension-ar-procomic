> **HISTORICAL / SUPERSEDED:** This handoff describes an earlier repository state and is not evidence for the current audit head [`f3f4290`](https://github.com/LoneVertex/mihon-extension-ar-procomic/commit/f3f4290d13f1bf204b0278e25a01235a77ba0087). Its branch, version, 119 KB APK-size, WebView, and validation claims are obsolete; the current deterministic gate also installs test-only `Pillow==12.3.0`. For current branch, PR, build, and validation information, use [`README.md`](../README.md), [`HANDOFF.md`](HANDOFF.md), [`VALIDATION.md`](VALIDATION.md), and [`BRANCH_TOPOLOGY.md`](BRANCH_TOPOLOGY.md).

# PROCOMIC EXTENSION — FINAL ENGINEERING HANDOFF TO MANUS AI

**Date:** August 2026
**Author:** Lead Engineer (Antigravity)
**Successor:** Manus AI
**Repository:** `https://github.com/lonevertex/mihon-extension-ar-procomic`

---

## 1. Repository

- **GitHub Repository:** `https://github.com/lonevertex/mihon-extension-ar-procomic`
- **Visibility:** Public
- **Default Branch:** `main`
- **Package Name:** `eu.kanade.tachiyomi.extension.ar.procomic`

---

## 2. Current Source State

- **Current Branch:** `feat/ar-procomic-extension` (published to `main`)
- **HEAD Commit SHA:** `c0bc39a` (plus documentation/hygiene commits)
- **Working Tree State:** Clean, build artifacts ignored, secret-scanned, verified build passing.

---

## 3. Build State

- **Build Command:** `./gradlew clean :app:assembleDebug`
- **Build Outcome:** `BUILD SUCCESSFUL in 28s`
- **APK Path:** `app/build/outputs/apk/debug/app-debug.apk`
- **APK Size:** ~119 KB (debug-signed, ready for device sideloading)
- **JVM Bytecode Target:** Java 1.8 (`minSdk = 24`, `targetSdk = 35`)

---

## 4. Completed Work

1. **Standalone Extension Pipeline:** Built fully working Gradle build environment using Kotlin 2.4.10, AGP 8.7.0, and `extensions-lib:6e0c96cea8`.
2. **Server-Side REST Search (`GET /api/public/series/search`):** Full query search with server-side URL encoding, pagination (`meta.page < meta.pages`), and type filter (`manga`, `manhwa`, `manhua`).
3. **REST Chapter System (`GET /api/chapters`):** Resolves chapters across legacy and new series IDs, eliminating RSC redirect failures and surfacing all approved Arabic/English releases.
4. **RSC Flight Stream Parsing:** Robust bracket-counting algorithm in `ProComicUtils.kt` extracting `initialSeries` and reader image URLs without full Next.js Flight runtime dependencies.
5. **WebView Isolation:** Per-request `rscHeaders()` allowing the in-app WebView to navigate HTML pages normally without breaking on streaming Flight component bodies.
6. **Robust DTOs:** Custom `StringOrListSerializer` handling polymorphic author/artist fields and null `total` values.

---

## 5. Unfinished Work & Future Scope

1. **Browse Infinite Scrolling:** The browse tabs (`Popular` / `Latest`) currently fetch the fixed 14-item RSC stream. Needs investigation into whether a paginated REST browse endpoint exists (e.g. `/api/public/series/list` or similar).
2. **Tag / Genre Search Filter:** ProComic's search backend currently ignores the `genre` parameter. If backend updates to support tags, filter mappings in `ProComic.kt` can be updated.
3. **Official Keiyoushi Upstream Submission:** Once Manus completes device verification, this extension can be submitted to `keiyoushi/extensions-source`.

---

## 6. Known Bugs & Limitations

| Severity | Issue | Workaround / Status |
|---|---|---|
| **P2 (Medium)** | Browse tabs only show newest 14 series (RSC window) | Use Search to browse all 700+ titles in database |
| **P3 (Minor)** | Genre filter not respected by search backend | Search by title or filter by Comic Type (Manga/Manhwa/Manhua) |
| **P3 (Minor)** | Novel series returned by raw API | Automatically filtered out by `type != "novel"` in code |

---

## 7. Major Discoveries (Do Not Repeat Old Mistakes)

1. **Search is Server-Side REST:** The browser does **NOT** search client-side. It calls `GET /api/public/series/search?search={query}&limit=18&page={page}`.
2. **Domain Migration:** `procomic.pro` proxies/redirects to `procomic.net`. Base URL is `https://procomic.net`.
3. **Chapter Listing is REST:** Older series return Next.js redirect tokens in their RSC streams (`NEXT_REDIRECT;replace;/ar/{slug}-{id};308;`). Do not parse chapter lists from RSC; always use `GET /api/chapters?contentId={id}`.
4. **Bilingual Chapter Catalogue:** Chapters are published in both Arabic (`AR`) and English (`EN`). Do not apply language filtering on chapters.

---

## 8. Verification Status Summary

| Area | Status | Evidence |
|---|---|---|
| **Build System** | ✅ VERIFIED | Clean Gradle build in 28s on JDK 21 |
| **Search API** | ✅ VERIFIED | Tested live against queries: `assassin`, `dragon`, `hunter`, `سيف`, `العالم` |
| **Chapter REST API** | ✅ VERIFIED | Tested live on multiple series IDs |
| **Mihon Manifest** | ✅ VERIFIED | Declares `tachiyomi.extension` and metadata |
| **Device End-to-End** | ⏳ READY FOR MANUS | APK built and ready for smoke test on device |

---

## 9. Recommended Next Action for Manus AI

### 👉 **Step 1:** Run a manual smoke test on an Android device or emulator with Mihon:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Open Mihon, search for `dragon` (54 matches expected), tap a result, refresh chapters, and verify reading chapter 1.
