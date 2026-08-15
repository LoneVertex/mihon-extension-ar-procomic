# ProComic Mihon Extension

[![CI Build](https://github.com/lonevertex/mihon-extension-ar-procomic/actions/workflows/ci.yml/badge.svg)](https://github.com/lonevertex/mihon-extension-ar-procomic/actions/workflows/ci.yml)
![Language](https://img.shields.io/badge/Language-Kotlin%202.4-blue.svg)
![Target](https://img.shields.io/badge/Platform-Tachiyomi%20%2F%20Mihon-green.svg)
![Status](https://img.shields.io/badge/Status-Alpha%20%2F%20Functional%20Prototype-orange.svg)

Standalone Android extension for [Mihon](https://github.com/mihonapp/mihon) / [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi) providing access to **ProComic** (`https://procomic.net`), an Arabic manga, manhwa, and manhua platform.

---

## 📌 Extension Details

- **Package Name:** `eu.kanade.tachiyomi.extension.ar.procomic`
- **Class:** `eu.kanade.tachiyomi.extension.ar.procomic.ProComic`
- **Language Code:** `ar` (Arabic catalog with Arabic & English releases)
- **Base Domain:** `https://procomic.net` (mirrored from `procomic.pro`)
- **Version Code:** `2`
- **Version Name:** `1.1`

---

## 🏗️ Architecture Overview

The ProComic website runs on Next.js with React Server Components (RSC) and backend REST microservices. The extension uses a hybrid architecture:

1. **Browse (Popular & Latest):** Fetches the initial streaming Flight RSC component tree (`text/x-component`) from `/ar/series?sort=popular|latest`. `ProComicUtils.kt` extracts the `initialSeries` JSON payload via a bracket-counting parser.
2. **Search:** Queries the server-side REST API (`GET /api/public/series/search?status=approved&limit=18&page={page}&sort=latest&search={query}&type={type}`) with JSON response mapping and dynamic pagination (`meta.page < meta.pages`).
3. **Chapter Listing:** Queries the REST endpoint (`GET /api/chapters?contentId={seriesId}&page={page}`) to list and paginate all published releases regardless of legacy routing or translation language.
4. **Reader (Pages):** Queries chapter reader RSC streams (`?_rsc=rdr`) to extract high-resolution image URLs.

---

## 🛠️ Development & Build Requirements

- **JDK:** Java 21 (Temurin / OpenJDK recommended)
- **Android SDK:** `minSdk = 24`, `compileSdk = 35`, `targetSdk = 35`
- **Kotlin:** 2.4.10 (`jvmTarget = JvmTarget.JVM_1_8`)
- **Gradle:** 8.14.4 (via included `./gradlew` wrapper)

### Building Debug APK

```bash
./gradlew clean :app:assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📲 Installation & Testing Workflow

1. Ensure USB Debugging is enabled on your Android device:
   ```bash
   adb devices
   ```
2. Build and install directly:
   ```bash
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. Open **Mihon** → **Browse** → **Sources** → **ProComic (AR)**.
4. Verify browse listing, search queries, series details, chapter list refresh, and page reading.

---

## 📋 Current Feature Status

| Feature | Status | Notes |
|---|---|---|
| **Popular Browse** | ✅ Functional | Shows newest 14 non-novel titles from RSC stream |
| **Latest Browse** | ✅ Functional | Client-side sorted by update timestamp |
| **Server-side Search** | ✅ Functional | Full catalog search against `/api/public/series/search` |
| **Series Details** | ✅ Functional | Cover, author, artist, status, descriptions |
| **Chapter Listing** | ✅ Functional | Paginated REST API loading all approved releases |
| **Reader / Pages** | ✅ Functional | RSC image stream extraction |
| **In-App WebView** | ✅ Functional | Clean browser navigation using standard headers |

---

## ⚠️ Known Limitations

- **Novel Filtering:** Text-only web novels (`type: "novel"`) are deliberately filtered out as Mihon is a comic reader.
- **Popular/Latest Fixed Window:** The server's `/ar/series` RSC stream only caches the ~18 most recently added series. Search should be used to browse the broader catalogue.
- **Genre Querying:** The backend search API does not currently filter by tags/genres; only `search` string and `type` (manga/manhwa/manhua) are evaluated server-side.

---

## 📖 Complete Engineering Documentation

For deep architectural notes, forensic analysis, investigation history, and handoff specifics, please read:
- [`docs/HANDOFF.md`](docs/HANDOFF.md) — Comprehensive technical handoff guide.
- [`docs/HANDOFF_FINAL.md`](docs/HANDOFF_FINAL.md) — Final summary and next engineering steps.

---

## 📄 License & Ownership

No explicit license has been assigned to this repository. All rights reserved by the original authors and upstream Keiyoushi / Tachiyomi project contributors.
