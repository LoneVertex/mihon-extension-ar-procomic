# ar.procomic Tachiyomi Extension — Implementation Plan

> **HISTORICAL / SUPERSEDED:** This dated implementation plan describes an earlier pre-remediation design. Preserve it as history; do not use it as current implementation guidance. Consult [`docs/PROCOMIC_SYSTEM.md`](../../PROCOMIC_SYSTEM.md) for the current system.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working standalone Tachiyomi/Mihon extension for procomic.pro that returns Popular, Latest, Search, series detail, chapter list, and page list (3 pages/chapter for guests).

**Architecture:** HttpSource subclass. RSC wire-format parsing (no HTML scraping, no REST API). OkHttp with mobile User-Agent + RSC headers. Kotlinx serialization for DTO parsing.

**Tech Stack:** Kotlin, Gradle (extension convention plugins), OkHttp, Kotlinx Serialization, Tachiyomi extensions-lib

## Global Constraints

- `baseUrl = "https://procomic.pro"`
- `lang = "ar"`
- `name = "ProComic"` (display name in Tachiyomi)
- `versionId = 1`, `extVersionCode = 1`
- `nsfw = false` (guests always have safe-browsing ON — confirmed by recon)
- Required headers on every request: `User-Agent: Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36`, `RSC: 1`, `Accept-Language: ar,en;q=0.9`
- No RSC library dependency in the APK — parse RSC responses with plain Kotlin Regex + kotlinx.serialization
- No NSFW preference toggle
- Novel type (`type == "novel"`) must be excluded from all results
- Chapter pages: guest mode returns up to `publicImageCount` pages (currently 3)
- Image CDN host: `cdn2.procomic.pro` for chapter pages; `app.procomic.pro` for covers

---

## Task 1: Project Scaffold (Gradle Module + AndroidManifest)

**Files:**
- Create: `src/ar/procomic/build.gradle`
- Create: `src/ar/procomic/AndroidManifest.xml`
- Create: `src/ar/procomic/res/mipmap-hdpi/ic_launcher.png` (placeholder icon — copy from any existing ar extension)

**Interfaces:**
- Produces: buildable Gradle module that `assembleDebug` can target

- [ ] **Step 1: Create build.gradle**

```groovy
// src/ar/procomic/build.gradle
apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'
apply plugin: 'kotlinx-serialization'

ext {
    extName = 'ProComic'
    pkgNameSuffix = 'ar.procomic'
    extClass = '.ProComic'
    extVersionCode = 1
    isNsfw = false
}

apply from: "$rootDir/common.gradle"
```

- [ ] **Step 2: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <meta-data
            android:name="source.class"
            android:value=".ProComic" />
        <meta-data
            android:name="source.factory"
            android:value="eu.kanade.tachiyomi.extension.ar.procomic.ProComic" />
    </application>
</manifest>
```

- [ ] **Step 3: Copy icon from a sibling Arabic extension**

```bash
# Find any ar/ extension with an icon, copy it as placeholder
ls src/ar/*/res/mipmap-hdpi/ic_launcher.png | head -1
cp "$(ls src/ar/*/res/mipmap-hdpi/ic_launcher.png | head -1)" \
   src/ar/procomic/res/mipmap-hdpi/ic_launcher.png
# Repeat for all mipmap-* densities
```

- [ ] **Step 4: Verify build compiles (empty extension)**

Create the minimum ProComic.kt stub:
```kotlin
// src/ar/procomic/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt
package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Response

class ProComic : HttpSource() {
    override val name = "ProComic"
    override val baseUrl = "https://procomic.pro"
    override val lang = "ar"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: org.jsoup.nodes.Element) = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector() = null
    override fun popularMangaSelector() = ""

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: org.jsoup.nodes.Element) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = null
    override fun latestUpdatesSelector() = ""

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: org.jsoup.nodes.Element) = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector() = null
    override fun searchMangaSelector() = ""

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
    override fun pageListParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
```

Run: `./gradlew :extensions:individual:ar:procomic:assembleDebug`
Expected: BUILD SUCCESSFUL (though not functional)

- [ ] **Step 5: Commit**

```bash
git add src/ar/procomic/
git commit -m "feat(ar/procomic): scaffold Gradle module and stub extension"
```

---

## Task 2: Data Transfer Objects (DTOs)

**Files:**
- Create: `src/ar/procomic/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicDto.kt`

**Interfaces:**
- Produces: `ProComicSeriesDto`, `ProComicChapterDto`, `ProComicPageDto` serializable data classes used by Tasks 3–7

- [ ] **Step 1: Write failing test (manual checklist substitute — no test harness)**

Define the expected JSON input from RSC and verify deserialization manually in a scratch script. Expected input sample from recon:
```json
{"id":688,"title":"Full Time Hunter: I Hunt the World","slug":"full-time-hunter-i-hunt-the-world","type":"manhua","status":"approved","thumbnail":"https://app.procomic.pro/series-cards/688/originals/1784911530979-9v4hve87fxu.avif"}
```

- [ ] **Step 2: Create ProComicDto.kt**

```kotlin
package eu.kanade.tachiyomi.extension.ar.procomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProComicSeriesDto(
    val id: Int,
    val title: String,
    val slug: String,
    val type: String,
    val status: String? = null,
    val thumbnail: String? = null,
    val cover: String? = null,
    val description: String? = null,
    val genres: List<ProComicGenreDto>? = null,
    val tags: List<ProComicTagDto>? = null,
)

@Serializable
data class ProComicGenreDto(
    val id: Int,
    val en: String,
    val ar: String,
)

@Serializable
data class ProComicTagDto(
    val id: Int,
    val en: String,
    val ar: String,
)

@Serializable
data class ProComicChapterDto(
    val id: Int,
    @SerialName("content_id") val contentId: Int,
    @SerialName("chapter_number") val chapterNumber: String,
    val title: String? = null,
    val language: String,
    val translator: String? = null,
    val status: String? = null,
    @SerialName("cdn_path") val cdnPath: String? = null,
    val metadata: ProComicChapterMetadata? = null,
)

@Serializable
data class ProComicChapterMetadata(
    @SerialName("protectionV2") val protection: ProComicProtection? = null,
)

@Serializable
data class ProComicProtection(
    @SerialName("publicImageCount") val publicImageCount: Int,
    val version: Int,
)
```

- [ ] **Step 3: Verify serialization compiles**

Run: `./gradlew :extensions:individual:ar:procomic:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/ar/procomic/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicDto.kt
git commit -m "feat(ar/procomic): add DTO data classes"
```

---

## Task 3: RSC Parser Utility

**Files:**
- Create: `src/ar/procomic/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicUtils.kt`

**Interfaces:**
- Consumes: `Response` from OkHttp (RSC response body as String)
- Produces:
  - `fun extractSeriesList(body: String): List<ProComicSeriesDto>`
  - `fun extractSeriesDetail(body: String): ProComicSeriesDto?`
  - `fun extractChapterList(body: String): List<ProComicChapterDto>`
  - `fun extractPageImages(body: String): List<String>` (image CDN URLs)

The RSC wire format is line-based. Each line starts with a hex-prefix reference ID followed by a colon and the payload. JSON objects and arrays are embedded inline in JSX element props.

- [ ] **Step 1: Write parser**

```kotlin
package eu.kanade.tachiyomi.extension.ar.procomic

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object ProComicUtils {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // Regex to find JSON arrays of series objects embedded in RSC stream
    // Matches: [...{"id":123,"title":"...","slug":"...","type":"...",...}...]
    private val SERIES_ARRAY_RE = Regex(
        """\[\{\"id\":\d+,\"title\":\"[^"]+\",\"slug\":\"[^"]+\",\"type\":\"[^"]+\"""" +
        """[^]]+\]""",
        RegexOption.DOT_MATCHES_ALL
    )

    // Series object regex for individual extraction
    private val SERIES_OBJ_RE = Regex(
        """\{\"id\":(\d+),\"title\":\"([^"]+)\",\"slug\":\"([^"]+)\",\"type\":\"([^"]+)\"[^}]*\"(?:thumbnail|cover)\":\"([^"]+)\"[^}]*\}"""
    )

    // Chapter object regex
    private val CHAPTER_OBJ_RE = Regex(
        """\{\"id\":(\d+),\"content_id\":\d+,\"chapter_number\":\"([^"]+)\"[^}]+\"language\":\"([^"]+)\"[^}]*\}"""
    )

    // Images array in chapter reader RSC
    private val IMAGES_ARRAY_RE = Regex(
        """"images\":\[([^\]]+)\]"""
    )

    /**
     * Extract series list from /ar/series RSC response body.
     * Filters out 'novel' type entries.
     */
    fun extractSeriesList(body: String): List<ProComicSeriesDto> {
        val results = mutableListOf<ProComicSeriesDto>()
        // Find JSON arrays containing series objects
        val arrayMatch = SERIES_ARRAY_RE.find(body)
        if (arrayMatch != null) {
            try {
                val list: List<ProComicSeriesDto> = json.decodeFromString(arrayMatch.value)
                results.addAll(list.filter { it.type != "novel" })
                return results
            } catch (_: Exception) { /* fall through to regex extraction */ }
        }
        // Fallback: extract individual objects with regex
        SERIES_OBJ_RE.findAll(body).forEach { match ->
            val type = match.groupValues[4]
            if (type != "novel") {
                results.add(
                    ProComicSeriesDto(
                        id = match.groupValues[1].toInt(),
                        title = match.groupValues[2],
                        slug = match.groupValues[3],
                        type = type,
                        thumbnail = match.groupValues[5],
                    )
                )
            }
        }
        return results
    }

    /**
     * Extract chapter list from series detail RSC.
     * Filters to AR language chapters only.
     */
    fun extractChapterList(body: String): List<ProComicChapterDto> {
        val chapters = mutableListOf<ProComicChapterDto>()
        CHAPTER_OBJ_RE.findAll(body).forEach { match ->
            val lang = match.groupValues[3]
            if (lang == "AR") {
                chapters.add(
                    ProComicChapterDto(
                        id = match.groupValues[1].toInt(),
                        contentId = 0, // Not needed for URL construction
                        chapterNumber = match.groupValues[2],
                        language = lang,
                    )
                )
            }
        }
        return chapters.sortedByDescending { it.chapterNumber.toFloatOrNull() ?: 0f }
    }

    /**
     * Extract page image URLs from chapter reader RSC.
     * Returns up to publicImageCount images (CDN enforces this server-side).
     */
    fun extractPageImages(body: String): List<String> {
        val imagesMatch = IMAGES_ARRAY_RE.find(body) ?: return emptyList()
        val urlsRaw = imagesMatch.groupValues[1]
        return Regex(""""(https://[^"]+)"""").findAll(urlsRaw)
            .map { it.groupValues[1] }
            .toList()
    }
}
```

- [ ] **Step 2: Verify compiles**

Run: `./gradlew :extensions:individual:ar:procomic:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/ar/procomic/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicUtils.kt
git commit -m "feat(ar/procomic): add RSC parser utility"
```

---

## Task 4: Core HttpSource — Popular and Latest

**Files:**
- Modify: `ProComic.kt` (replace stub with real implementation)

**Interfaces:**
- Consumes: `ProComicUtils.extractSeriesList()`, `ProComicSeriesDto`
- Produces: Working `popularMangaRequest`, `latestUpdatesRequest`, `popularMangaParse`, `latestUpdatesParse`

The extension extends `HttpSource` (not `ParsedHttpSource`), so we override `popularMangaParse(response)` directly.

- [ ] **Step 1: Replace ProComic.kt stub with real implementation**

```kotlin
package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ProComic : HttpSource() {
    override val name = "ProComic"
    override val baseUrl = "https://procomic.pro"
    override val lang = "ar"
    override val supportsLatest = true

    private val baseHeaders = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .add("RSC", "1")
        .add("Accept-Language", "ar,en;q=0.9")
        .add("Next-Router-State-Tree", "%5B%22%22%2C%7B%7D%5D")
        .build()

    // ---- Popular ----
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/ar/series?sort=popular&page=$page&_rsc=${page}"
        return Request.Builder().url(url).headers(baseHeaders).build()
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        val series = ProComicUtils.extractSeriesList(body)
        val mangas = series.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = mangas.size >= 20)
    }

    // ---- Latest ----
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/ar/series?sort=latest&page=$page&_rsc=${page + 100}"
        return Request.Builder().url(url).headers(baseHeaders).build()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val body = response.body.string()
        val series = ProComicUtils.extractSeriesList(body)
        val mangas = series.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = mangas.size >= 20)
    }

    // ---- Search (Task 5) ----
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/ar/series?search=${query.trim().encodeURL()}&page=$page&_rsc=${page + 200}"
        return Request.Builder().url(url).headers(baseHeaders).build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ---- Detail (Task 6) ----
    override fun mangaDetailsRequest(manga: SManga): Request {
        return Request.Builder()
            .url("$baseUrl${manga.url}?_rsc=detail")
            .headers(baseHeaders)
            .build()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body.string()
        val dto = ProComicUtils.extractSeriesDetail(body)
            ?: throw Exception("Could not parse series details")
        return dto.toSManga()
    }

    // ---- Chapter list (Task 6) ----
    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        return ProComicUtils.extractChapterList(body).map { it.toSChapter() }
    }

    // ---- Page list (Task 7) ----
    override fun pageListRequest(chapter: SChapter): Request {
        return Request.Builder()
            .url("$baseUrl${chapter.url}?_rsc=pages")
            .headers(baseHeaders)
            .build()
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val images = ProComicUtils.extractPageImages(body)
        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ---- URL building helpers ----
    private fun ProComicSeriesDto.toSManga() = SManga.create().apply {
        url = "/ar/series/$type/$id/$slug"
        title = this@toSManga.title
        thumbnail_url = this@toSManga.thumbnail ?: this@toSManga.cover
        genre = (this@toSManga.genres?.map { it.en } ?: emptyList())
            .plus(this@toSManga.type.replaceFirstChar { it.uppercase() })
            .joinToString(", ")
        description = this@toSManga.description
        status = when (this@toSManga.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "dropped", "cancelled" -> SManga.CANCELLED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun ProComicChapterDto.toSChapter() = SChapter.create().apply {
        // URL pattern: /ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNumber}
        // We store the chapterId in the URL — series context is reconstructed from manga.url
        url = "/chapters/$id"
        name = "Chapter $chapterNumber${if (!title.isNullOrBlank()) " - $title" else ""}"
        chapter_number = chapterNumber.toFloatOrNull() ?: -1f
        scanlator = translator ?: "Pro Chan"
    }

    private fun String.encodeURL(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
```

Note: The `chapter.url` stores just `/chapters/{id}` — the `pageListRequest` needs the full series URL to construct the reader URL. We'll fix this in Task 7.

- [ ] **Step 2: Add extractSeriesDetail to ProComicUtils.kt**

```kotlin
// Add to ProComicUtils object:
fun extractSeriesDetail(body: String): ProComicSeriesDto? {
    // Try direct JSON deserialization of the series object
    val seriesObjRe = Regex(
        """\{"id":(\d+),"title":"([^"]+)","slug":"([^"]+)","type":"([^"]+)"""" +
        """.*?"(?:thumbnail|cover)":"([^"]+)""""
    )
    val match = seriesObjRe.find(body) ?: return null

    // Extract description (usually in a T-block with Arabic text)
    val descRe = Regex("""(?s)T[0-9a-f]+,([^\n\r]{50,2000})""")
    val desc = descRe.find(body)?.groupValues?.get(1)?.trim()

    // Extract genres
    val genreListRe = Regex("""\[\{"id":(\d+),"en":"([^"]+)","ar":"([^"]+)"""" +
        ""","descriptionEn":""")
    val genres = genreListRe.findAll(body).map { m ->
        ProComicGenreDto(
            id = m.groupValues[1].toInt(),
            en = m.groupValues[2],
            ar = m.groupValues[3],
        )
    }.toList()

    return ProComicSeriesDto(
        id = match.groupValues[1].toInt(),
        title = match.groupValues[2],
        slug = match.groupValues[3],
        type = match.groupValues[4],
        thumbnail = match.groupValues[5],
        description = desc,
        genres = genres.ifEmpty { null },
    )
}
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew :extensions:individual:ar:procomic:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Functional check — Popular**

Install the debug APK on a device/emulator and verify:
- Open Tachiyomi → Browse → ProComic → Popular
- Expected: Non-empty list of manga (≥1 result, titles match site content)
- If empty: check logcat for network errors or parsing failures

- [ ] **Step 5: Commit**

```bash
git add src/ar/procomic/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/
git commit -m "feat(ar/procomic): implement Popular and Latest via RSC parsing"
```

---

## Task 5: Search

**Interfaces:**
- Consumes: `searchMangaRequest` (already in ProComic.kt from Task 4)
- Produces: Verified working search by title

The search request is already wired. This task verifies the actual URL parameter and confirms the RSC response format for search results.

- [ ] **Step 1: Verify search endpoint parameter**

```bash
curl -s -L "https://procomic.pro/ar/series?search=breakers&_rsc=search1" \
  -H "User-Agent: Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36" \
  -H "RSC: 1" \
  -H "Accept-Language: ar,en;q=0.9" \
  --max-time 20 > /tmp/search_test.txt
echo "Search result size: $(wc -c < /tmp/search_test.txt) bytes"
grep -c '"slug"' /tmp/search_test.txt
```

Expected: Non-zero number of slug matches = search works.
If zero: try `q=`, `query=`, `keyword=` parameter names until one works.

- [ ] **Step 2: Update searchMangaRequest if param name differs**

If Step 1 shows the param is not `search=`, update ProComic.kt:
```kotlin
override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
    val url = "$baseUrl/ar/series?CORRECT_PARAM=${query.trim().encodeURL()}&page=$page&_rsc=${page + 200}"
    return Request.Builder().url(url).headers(baseHeaders).build()
}
```

- [ ] **Step 3: Build and functional test**

Run: `./gradlew :extensions:individual:ar:procomic:assembleDebug`
Functional test: Open Tachiyomi → Browse → ProComic → Search → type "hunter"
Expected: Returns relevant results

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(ar/procomic): verify and implement Search endpoint"
```

---

## Task 6: Series Detail + Chapter List

**Interfaces:**
- Consumes: `manga.url` format: `/ar/series/{type}/{id}/{slug}`
- Produces: `mangaDetailsParse` and `chapterListParse` returning populated SManga + SChapter list

The chapter URL stored in SChapter must encode enough information for the page list request. Update the URL scheme to store the full reader path.

- [ ] **Step 1: Fix chapter URL scheme**

In ProComic.kt, update `ProComicChapterDto.toSChapter()`:

The problem: `pageListRequest` needs the full series context (`/ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNum}`), but `SChapter.url` only stores `/chapters/{id}`.

Solution: Store the full reader path in `SChapter.url`:
```kotlin
private fun ProComicChapterDto.toSChapter(mangaUrl: String) = SChapter.create().apply {
    // mangaUrl is /ar/series/{type}/{id}/{slug}
    // Build: /ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNumber}
    url = "$mangaUrl/$id/$chapterNumber"
    name = "Chapter $chapterNumber${if (!title.isNullOrBlank()) " - $title" else ""}"
    chapter_number = chapterNumber.toFloatOrNull() ?: -1f
    scanlator = translator ?: "Pro Chan"
}
```

Update `chapterListParse` to pass manga.url:

Since `chapterListParse(response)` doesn't have access to the manga URL directly, use an `Interceptor` to pass it, or alternatively store a temp var. The cleanest approach: override `getChapterList(manga)` to pass manga.url into a thread-local or use response URL.

Better approach — use response URL to reconstruct series path:
```kotlin
override fun chapterListParse(response: Response): List<SChapter> {
    val body = response.body.string()
    // Reconstruct the manga URL from the response URL (remove ?_rsc=detail suffix)
    val mangaUrl = response.request.url.toString()
        .removePrefix(baseUrl)
        .substringBefore("?")
    return ProComicUtils.extractChapterList(body).map { it.toSChapter(mangaUrl) }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :extensions:individual:ar:procomic:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Functional test**

Open Tachiyomi → Browse → ProComic → tap any series
Expected:
- Title, description, cover populated
- Genres listed
- Chapter list shows at least 1 chapter
- Chapter names formatted as "Chapter 4"

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(ar/procomic): implement series detail and chapter list"
```

---

## Task 7: Page List (Chapter Reader)

**Interfaces:**
- Consumes: `chapter.url` = `/ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNum}`
- Produces: `pageListParse` returning 1–3 `Page` objects with `imageUrl` set

- [ ] **Step 1: Verify page list RSC request works**

```bash
curl -s -L "https://procomic.pro/ar/series/manhua/688/full-time-hunter-i-hunt-the-world/50675/4?_rsc=pages" \
  -H "User-Agent: Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36" \
  -H "RSC: 1" \
  -H "Accept-Language: ar,en;q=0.9" \
  --max-time 20 | grep -oP 'cdn2\.procomic\.(pro|net)/[^\s"]+' | head -10
```

Expected: 3 image paths from `cdn2.procomic.pro`

- [ ] **Step 2: Test if images load with Referer**

The CDN returned 403 in recon. Check if `Referer: https://procomic.pro/` + full page URL resolves the issue when OkHttp has a session from prior browsing.

Update `pageListParse` to add `Referer` header to image URLs via custom headers:
```kotlin
override fun imageRequest(page: Page): Request {
    val headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .add("Referer", "https://procomic.pro/")
        .add("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
        .build()
    return Request.Builder().url(page.imageUrl!!).headers(headers).build()
}
```

- [ ] **Step 3: Build and test**

Run: `./gradlew :extensions:individual:ar:procomic:assembleDebug`
Install APK and test reading a chapter.
Expected: 3 pages load successfully (or 403 error displayed — see escalation note)

**If still 403:** This is a confirmed escalation point (CDN requires authenticated session). Implement fallback: return the RSC reader URL as the page URL so Tachiyomi can open in browser:
```kotlin
// Fallback in pageListParse if images are empty:
if (images.isEmpty()) {
    return listOf(Page(0, url = response.request.url.toString().substringBefore("?")))
}
```

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(ar/procomic): implement page list"
```

---

## Task 8: Filters

**Files:**
- Create: `src/ar/procomic/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicFilters.kt`

**Interfaces:**
- Produces: `FilterList` for `getFilterList()` with Type and Genre filters

- [ ] **Step 1: Create ProComicFilters.kt**

```kotlin
package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter : Filter.Select<String>(
    "Type",
    arrayOf("All", "Manga", "Manhwa", "Manhua"),
    0
)

class GenreFilter(genres: List<Pair<String, String>>) : Filter.Select<String>(
    "Genre",
    genres.map { it.first }.toTypedArray(),
    0
) {
    val genreValues = genres.map { it.second }
}

// Genres from recon — top 20 most common:
val GENRES = listOf(
    Pair("All", ""),
    Pair("Action", "1"),
    Pair("Adventure", "3"),
    Pair("Comedy", "4"),
    Pair("Drama", "7"),
    Pair("Fantasy", "9"),
    Pair("Horror", "12"),
    Pair("Mystery", "14"),
    Pair("Romance", "15"),
    Pair("Sci-Fi", "17"),
    Pair("School Life", "16"),
    Pair("Shounen", "20"),
    Pair("Slice of Life", "21"),
    Pair("Supernatural", "22"),
    Pair("Martial Arts", "13"),
    Pair("Isekai", "11"),
    Pair("Reincarnation", "15"),
    Pair("System", "23"),
    Pair("Tower", "24"),
    Pair("Overpowered MC", "25"),
)
```

- [ ] **Step 2: Wire filters into ProComic.kt**

```kotlin
override fun getFilterList() = FilterList(
    TypeFilter(),
    GenreFilter(GENRES),
)

override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
    var typeParam = ""
    var genreParam = ""

    filters.forEach { filter ->
        when (filter) {
            is TypeFilter -> {
                if (filter.state > 0) {
                    typeParam = "&type=${filter.values[filter.state].lowercase()}"
                }
            }
            is GenreFilter -> {
                if (filter.state > 0) {
                    genreParam = "&genre=${filter.genreValues[filter.state]}"
                }
            }
            else -> {}
        }
    }

    val searchParam = if (query.isNotBlank()) "&search=${query.trim().encodeURL()}" else ""
    val url = "$baseUrl/ar/series?page=$page$searchParam$typeParam$genreParam&_rsc=${page + 300}"
    return Request.Builder().url(url).headers(baseHeaders).build()
}
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew :extensions:individual:ar:procomic:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Functional test**

Open Tachiyomi → Browse → ProComic → Filter → select "Manhwa"
Expected: Results show only manhwa type

- [ ] **Step 5: Full integration build**

Run: `./gradlew :extensions:individual:ar:procomic:assembleDebug`
Run full functional checklist:
- [ ] Popular returns ≥1 result
- [ ] Latest returns ≥1 result
- [ ] Search "hunter" returns relevant results
- [ ] Series detail has title + cover + description
- [ ] Chapter list has ≥1 chapter
- [ ] Page list returns pages (even if just 3)
- [ ] Type filter changes results
- [ ] No crashes on 403 image (graceful error or browser fallback)

- [ ] **Step 6: Final commit**

```bash
git add src/ar/procomic/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicFilters.kt
git commit -am "feat(ar/procomic): add type and genre filters, complete implementation"
```

---

## Stage 9 — Completion Checklist

- [ ] All 8 tasks passed individually
- [ ] Integration build successful
- [ ] Functional checklist all green
- [ ] No secrets/credentials in code
- [ ] `nsfw=false` in build.gradle (confirmed)
- [ ] No NSFW preference toggle
- [ ] Known limitation documented: chapter pages limited to 3 for guests
- [ ] PR description ready with root cause + validation results
