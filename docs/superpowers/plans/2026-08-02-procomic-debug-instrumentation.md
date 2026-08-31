# ProComic Debug Instrumentation Implementation Plan

> **AUDIT ARTIFACT / HISTORICAL:** This dated instrumentation plan records an earlier diagnostic phase. It is retained for provenance and is not current implementation guidance; consult [`docs/PROCOMIC_SYSTEM.md`](../../PROCOMIC_SYSTEM.md) for the current system.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add complete runtime instrumentation to the RSC parse pipeline so every stage between OkHttp response receipt and `MangasPage` construction is observable via logcat — proving exactly where execution diverges from the curl baseline.

**Architecture:** Single new file `ProComicDiag.kt` holds all diagnostic helpers. `ProComicUtils.kt` and `ProComic.kt` are modified additively only — no logic changes, only logging calls inserted. All instrumentation is guarded behind the `ProComicDiag` object so it can be removed in one commit.

**Tech Stack:** Kotlin, Android `android.util.Log` (always available in extension APKs), `java.security.MessageDigest` (stdlib, no new dependency).

## Global Constraints

- ZERO logic changes — no changed return values, no changed branching, no changed error behavior
- ZERO domain changes — `baseUrl` stays `https://procomic.pro`
- ZERO DTO changes — no field additions or removals
- ZERO parser algorithm changes — bracket-counting logic is untouched
- Log tag MUST be `"ProComicDiag"` (filterable with `adb logcat -s ProComicDiag`)
- All silent `catch (e: Exception) { emptyList() }` blocks remain — logging added BEFORE the return
- Every new parameter MUST have a default value so existing callers compile unchanged

---

## File Map

| File | Action | Responsibility |
|------|--------|----------------|
| `…/procomic/ProComicDiag.kt` | **Create** | SHA-256 helper, HTTP response logger, parser stage logger, exception logger |
| `…/procomic/ProComicUtils.kt` | **Modify** | Add `diagTag`/`diagUrl` params with defaults, insert stage logs, replace silent catches |
| `…/procomic/ProComic.kt` | **Modify** | Call `ProComicDiag.logResponse()` in each parse method, forward tag+URL to extractors |

---

## Task 1 — Create `ProComicDiag.kt`

**Files:**
- Create: `app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicDiag.kt`

**Interfaces:**
- Produces:
  - `ProComicDiag.TAG: String = "ProComicDiag"`
  - `ProComicDiag.sha256(s: String): String`
  - `ProComicDiag.logResponse(tag: String, response: Response, body: String): Unit`
  - `ProComicDiag.logStage(tag: String, stage: Int, message: String): Unit`
  - `ProComicDiag.logException(tag: String, stage: String, url: String, e: Throwable): Unit`

- [ ] **Step 1: Create the file with full content**

```kotlin
package eu.kanade.tachiyomi.extension.ar.procomic

import android.util.Log
import okhttp3.Response
import java.security.MessageDigest

/**
 * INSTRUMENTATION-ONLY — remove before publishing to keiyoushi repo.
 *
 * Runtime diagnostic helper for the ProComic RSC parse pipeline.
 *
 * Filter logcat with:
 *   adb logcat -s ProComicDiag
 *
 * Stages logged:
 *   HTTP: URL, status, all request+response headers, body size, SHA-256, snippets
 *   Parser: key search, array extraction, deserialization, filter, item count
 *   Exceptions: type, message, full stack trace, stage, URL
 */
object ProComicDiag {

    const val TAG = "ProComicDiag"

    private const val SEP = "══════════════════════════════════════════════"

    // ── SHA-256 ──────────────────────────────────────────────────────────────

    fun sha256(s: String): String = try {
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "SHA256_ERROR:${e.message}"
    }

    // ── HTTP Response Logger ──────────────────────────────────────────────────

    /**
     * Log every observable HTTP property of [response] together with the
     * already-decoded [body] string.  Call immediately after
     * `response.body!!.string()` in each parse method.
     *
     * Captures:
     * - Request URL (exactly what OkHttp sent)
     * - HTTP status code
     * - All request headers (confirms RSC:1 presence)
     * - All response headers (Content-Type, Content-Encoding, cf-* etc.)
     * - Declared Content-Length header vs actual decoded body length (detects gzip)
     * - Body SHA-256 (compare against curl baseline: /tmp/ondevice_rsc.txt)
     * - Body first 500 chars (detect HTML vs RSC wire format vs Cloudflare page)
     * - Body last 200 chars
     */
    fun logResponse(tag: String, response: Response, body: String) {
        val url              = response.request.url.toString()
        val status           = response.code
        val contentType      = response.header("Content-Type")      ?: "(absent)"
        val contentEncoding  = response.header("Content-Encoding")  ?: "(absent)"
        val transferEncoding = response.header("Transfer-Encoding") ?: "(absent)"
        val contentLenHdr    = response.header("Content-Length")    ?: "(absent)"
        val cfCacheStatus    = response.header("cf-cache-status")   ?: "(absent)"
        val cfRay            = response.header("cf-ray")            ?: "(absent)"
        val xPoweredBy       = response.header("x-powered-by")      ?: "(absent)"
        val bodyLen          = body.length
        val bodyHash         = sha256(body)
        val bodyFirst500     = body.take(500).replace("\n", "↵").replace("\r", "")
        val bodyLast200      = body.takeLast(200).replace("\n", "↵").replace("\r", "")

        Log.d(TAG, SEP)
        Log.d(TAG, "[$tag] ── HTTP RESPONSE ──")
        Log.d(TAG, "[$tag] URL: $url")
        Log.d(TAG, "[$tag] Status: $status")
        Log.d(TAG, "[$tag] Content-Type: $contentType")
        Log.d(TAG, "[$tag] Content-Encoding: $contentEncoding")
        Log.d(TAG, "[$tag] Transfer-Encoding: $transferEncoding")
        Log.d(TAG, "[$tag] Content-Length (header): $contentLenHdr")
        Log.d(TAG, "[$tag] Body length (post-decompress): $bodyLen")
        Log.d(TAG, "[$tag] Body SHA-256: $bodyHash")
        Log.d(TAG, "[$tag] cf-cache-status: $cfCacheStatus")
        Log.d(TAG, "[$tag] cf-ray: $cfRay")
        Log.d(TAG, "[$tag] x-powered-by: $xPoweredBy")

        Log.d(TAG, "[$tag] ── REQUEST HEADERS ──")
        response.request.headers.forEach { (name, value) ->
            Log.d(TAG, "[$tag]   req> $name: $value")
        }

        Log.d(TAG, "[$tag] ── RESPONSE HEADERS ──")
        response.headers.forEach { (name, value) ->
            Log.d(TAG, "[$tag]   res> $name: $value")
        }

        Log.d(TAG, "[$tag] ── BODY SNIPPETS ──")
        Log.d(TAG, "[$tag] first500: $bodyFirst500")
        Log.d(TAG, "[$tag] last200:  $bodyLast200")
        Log.d(TAG, SEP)
    }

    // ── Parser Stage Logger ────────────────────────────────────────────────────

    fun logStage(tag: String, stage: Int, message: String) {
        Log.d(TAG, "[$tag][S$stage] $message")
    }

    // ── Exception Logger ──────────────────────────────────────────────────────

    /**
     * Log a caught exception with full context.  Does NOT rethrow — the caller
     * must still handle it (return emptyList, null, etc.) exactly as before.
     */
    fun logException(tag: String, stage: String, url: String, e: Throwable) {
        Log.e(TAG, "[$tag] *** EXCEPTION ***  stage=$stage")
        Log.e(TAG, "[$tag]   url=$url")
        Log.e(TAG, "[$tag]   type=${e.javaClass.name}")
        Log.e(TAG, "[$tag]   message=${e.message}")
        val cause = e.cause
        if (cause != null) {
            Log.e(TAG, "[$tag]   cause.type=${cause.javaClass.name}")
            Log.e(TAG, "[$tag]   cause.msg=${cause.message}")
        }
        Log.e(TAG, "[$tag]   stacktrace:", e)
    }
}
```

- [ ] **Step 2: Verify file compiles**

```bash
cd '/home/lonevertex/Documents/Projects/ProComic Extension'
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 0 errors.

---

## Task 2 — Instrument `ProComicUtils.kt`

**Files:**
- Modify: `app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicUtils.kt`

**Interfaces:**
- Consumes: `ProComicDiag.{TAG, sha256, logStage, logException}` from Task 1
- Produces (all with default params — existing callers unchanged):
  - `extractSeriesList(body, diagTag="", diagUrl="")`
  - `extractSeriesDetail(body, diagTag="", diagUrl="")`
  - `extractChapterList(body, diagTag="", diagUrl="")`
  - `extractPageImages(body, diagTag="", diagUrl="")`

- [ ] **Step 3: Add `import android.util.Log` and rewrite `extractSeriesList`**

Replace the entire file header + `extractSeriesList` function (lines 1–54):

```kotlin
package eu.kanade.tachiyomi.extension.ar.procomic

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * RSC (React Server Components) wire-format parser for procomic.pro.
 * …(existing javadoc unchanged)…
 */
object ProComicUtils {

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    // ---- Series List Extraction ----

    /**
     * Extract the list of series from the /ar/series RSC response.
     * …(existing javadoc unchanged)…
     *
     * @param diagTag  Logcat stage tag (e.g. "POPULAR"). Empty string suppresses logging.
     * @param diagUrl  Request URL for exception context.
     */
    fun extractSeriesList(
        body: String,
        diagTag: String = "",
        diagUrl: String = "",
    ): List<ProComicSeriesDto> {
        val diag = diagTag.isNotEmpty()
        if (diag) {
            ProComicDiag.logStage(diagTag, 1,
                "extractSeriesList: bodyLen=${body.length}, sha256=${ProComicDiag.sha256(body)}")
        }

        val keyPattern = "\"initialSeries\":["
        val keyIdx = body.indexOf(keyPattern)
        if (diag) {
            if (keyIdx >= 0) {
                ProComicDiag.logStage(diagTag, 2, "\"initialSeries\":[ FOUND at index=$keyIdx")
            } else {
                ProComicDiag.logStage(diagTag, 2,
                    "\"initialSeries\":[ NOT FOUND  bodyLen=${body.length}")
            }
        }

        val seriesJson = extractJsonArrayAfterKey(body, "initialSeries") ?: if (body.length > 1000) {
            throw Exception(
                "ProComic: 'initialSeries' key not found in RSC response " +
                "(${body.length} bytes). Site may have updated."
            )
        } else {
            if (diag) ProComicDiag.logStage(diagTag, 3,
                "body too short (${body.length} chars) — returning emptyList")
            return emptyList()
        }

        if (diag) {
            ProComicDiag.logStage(diagTag, 3,
                "extractJsonArray OK: jsonLen=${seriesJson.length}, " +
                "sha256=${ProComicDiag.sha256(seriesJson)}")
            ProComicDiag.logStage(diagTag, 3,
                "json first 200: ${seriesJson.take(200)}")
        }

        return try {
            if (diag) ProComicDiag.logStage(diagTag, 4,
                "decodeFromString<List<ProComicSeriesDto>> START")
            val decoded = json.decodeFromString<List<ProComicSeriesDto>>(seriesJson)
            if (diag) ProComicDiag.logStage(diagTag, 4,
                "decodeFromString SUCCESS: ${decoded.size} items")

            val filtered = decoded.filter { it.type != "novel" }
            if (diag) {
                ProComicDiag.logStage(diagTag, 5,
                    "filter(type!=novel): ${decoded.size} → ${filtered.size} items")
                filtered.forEachIndexed { i, dto ->
                    ProComicDiag.logStage(diagTag, 5,
                        "  [$i] id=${dto.id}, type=${dto.type}, " +
                        "title=${dto.title.take(40)}")
                }
            }
            filtered
        } catch (e: Exception) {
            if (diag) ProComicDiag.logException(diagTag,
                "decodeFromString<List<ProComicSeriesDto>>", diagUrl, e)
            emptyList()
        }
    }
```

- [ ] **Step 4: Rewrite `extractSeriesDetail` with logging params**

Replace the existing `extractSeriesDetail` function body:

```kotlin
    /**
     * Extract series detail from a series detail RSC response.
     * …(existing javadoc unchanged)…
     *
     * @param diagTag  Logcat stage tag (e.g. "DETAIL"). Empty string suppresses logging.
     * @param diagUrl  Request URL for exception context.
     */
    fun extractSeriesDetail(
        body: String,
        diagTag: String = "",
        diagUrl: String = "",
    ): ProComicSeriesDto? {
        val diag = diagTag.isNotEmpty()
        if (diag) ProComicDiag.logStage(diagTag, 1,
            "extractSeriesDetail: bodyLen=${body.length}")

        val listResult = extractSeriesList(body, diagTag, diagUrl)
        if (listResult.isNotEmpty()) {
            if (diag) ProComicDiag.logStage(diagTag, 2,
                "array path: found ${listResult.size} items, returning first")
            return listResult.first()
        }
        if (diag) ProComicDiag.logStage(diagTag, 2,
            "array path empty — trying single-object fallback")

        val idx = body.indexOf("\"id\":")
        if (idx < 0) {
            if (diag) ProComicDiag.logStage(diagTag, 3,
                "\"id\": NOT FOUND — returning null")
            return null
        }

        val start = body.lastIndexOf("{", idx).takeIf { it >= 0 } ?: return null
        if (diag) ProComicDiag.logStage(diagTag, 3,
            "object brace at index=$start (id-key at $idx)")

        val objJson = extractJsonObject(body, start) ?: run {
            if (diag) ProComicDiag.logStage(diagTag, 3,
                "extractJsonObject returned null")
            return null
        }
        if (diag) ProComicDiag.logStage(diagTag, 3,
            "extractJsonObject OK: len=${objJson.length}")

        return try {
            if (diag) ProComicDiag.logStage(diagTag, 4,
                "decodeFromString<ProComicSeriesDto> START")
            val dto = json.decodeFromString<ProComicSeriesDto>(objJson)
                .takeIf { it.type != "novel" }
            if (diag) ProComicDiag.logStage(diagTag, 4,
                "decodeFromString result: id=${dto?.id}, type=${dto?.type}")
            dto
        } catch (e: Exception) {
            if (diag) ProComicDiag.logException(diagTag,
                "decodeFromString<ProComicSeriesDto>", diagUrl, e)
            null
        }
    }
```

- [ ] **Step 5: Rewrite `extractChapterList` with logging params**

```kotlin
    // ---- Chapter List Extraction ----

    /**
     * …(existing javadoc unchanged)…
     *
     * @param diagTag  Logcat stage tag. Empty string suppresses logging.
     * @param diagUrl  Request URL for exception context.
     */
    fun extractChapterList(
        body: String,
        diagTag: String = "",
        diagUrl: String = "",
    ): List<ProComicChapterDto> {
        val diag = diagTag.isNotEmpty()
        if (diag) ProComicDiag.logStage(diagTag, 1,
            "extractChapterList: bodyLen=${body.length}")

        val chapIdx  = body.indexOf("\"chapters\":[")
        val initIdx  = body.indexOf("\"initialChapters\":[")
        if (diag) {
            ProComicDiag.logStage(diagTag, 2, "\"chapters\":[ index=$chapIdx")
            ProComicDiag.logStage(diagTag, 2, "\"initialChapters\":[ index=$initIdx")
        }

        val chaptersJson = extractJsonArrayAfterKey(body, "chapters")
            ?: extractJsonArrayAfterKey(body, "initialChapters")
            ?: throw Exception(
                "ProComic: 'chapters'/'initialChapters' key not found in RSC response. " +
                "Site may have updated."
            )

        if (diag) ProComicDiag.logStage(diagTag, 3,
            "chaptersJson len=${chaptersJson.length}")

        return try {
            if (diag) ProComicDiag.logStage(diagTag, 4,
                "decodeFromString<List<ProComicChapterDto>> START")
            val decoded = json.decodeFromString<List<ProComicChapterDto>>(chaptersJson)
            if (diag) ProComicDiag.logStage(diagTag, 4,
                "decodeFromString: ${decoded.size} total")

            val arOnly = decoded.filter { it.language == "AR" }
            if (diag) ProComicDiag.logStage(diagTag, 5,
                "AR filter: ${decoded.size} → ${arOnly.size}")

            val sorted = arOnly.sortedByDescending {
                it.chapterNumber.toFloatOrNull() ?: 0f
            }
            if (diag) ProComicDiag.logStage(diagTag, 6,
                "returning ${sorted.size} chapters")
            sorted
        } catch (e: Exception) {
            if (diag) ProComicDiag.logException(diagTag,
                "decodeFromString<List<ProComicChapterDto>>", diagUrl, e)
            emptyList()
        }
    }
```

- [ ] **Step 6: Rewrite `extractPageImages` with logging params**

```kotlin
    // ---- Page Image Extraction ----

    /**
     * …(existing javadoc unchanged)…
     *
     * @param diagTag  Logcat stage tag. Empty string suppresses logging.
     * @param diagUrl  Request URL for exception context.
     */
    fun extractPageImages(
        body: String,
        diagTag: String = "",
        diagUrl: String = "",
    ): List<String> {
        val diag = diagTag.isNotEmpty()
        val imagesKeyIdx = body.indexOf("\"images\":[")
        if (diag) ProComicDiag.logStage(diagTag, 1,
            "extractPageImages: bodyLen=${body.length}, " +
            "\"images\":[ at=$imagesKeyIdx")

        val imagesJson = extractJsonArrayAfterKey(body, "images") ?: run {
            if (diag) ProComicDiag.logStage(diagTag, 2,
                "\"images\":[ NOT FOUND — returning emptyList")
            return emptyList()
        }
        if (diag) ProComicDiag.logStage(diagTag, 2,
            "imagesJson: len=${imagesJson.length}, " +
            "first100=${imagesJson.take(100)}")

        return try {
            val arr = json.parseToJsonElement(imagesJson)
            if (arr is JsonArray) {
                val urls = arr.mapNotNull { element ->
                    element.jsonPrimitive.takeIf { it.isString }?.content
                        ?.takeIf { it.startsWith("https://") }
                }
                if (diag) ProComicDiag.logStage(diagTag, 3,
                    "parsed ${urls.size} image URLs")
                urls
            } else {
                if (diag) ProComicDiag.logStage(diagTag, 3,
                    "imagesJson is not a JsonArray")
                emptyList()
            }
        } catch (e: Exception) {
            if (diag) ProComicDiag.logException(diagTag,
                "parseToJsonElement(images)", diagUrl, e)
            // Fallback: regex for CDN URLs
            val fallback = Regex(
                "\"(https://[^\"]+\\.procomic\\.(pro|net)/[^\"]+\\.(avif|webp|jpg|jpeg|png))\""
            )
                .findAll(body)
                .map { it.groupValues[1] }
                .toList()
            if (diag) ProComicDiag.logStage(diagTag, 3,
                "regex fallback: ${fallback.size} URLs")
            fallback
        }
    }
```

- [ ] **Step 7: Verify ProComicUtils.kt compiles**

```bash
cd '/home/lonevertex/Documents/Projects/ProComic Extension'
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 0 errors.

---

## Task 3 — Instrument `ProComic.kt`

**Files:**
- Modify: `app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt`

- [ ] **Step 8: Replace `popularMangaParse` (lines 85–91)**

```kotlin
    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body!!.string()
        val url = response.request.url.toString()
        ProComicDiag.logResponse("POPULAR", response, body)
        val series = ProComicUtils.extractSeriesList(body, "POPULAR", url)
        val mangas = series.map { it.toSManga() }
        ProComicDiag.logStage("POPULAR", 99,
            "MangasPage: ${mangas.size} items, hasNextPage=${mangas.size >= 20}")
        return MangasPage(mangas, hasNextPage = mangas.size >= 20)
    }
```

- [ ] **Step 9: Replace `latestUpdatesParse` (lines 98–100)** — must NOT delegate to popularMangaParse so it logs its own tag

```kotlin
    override fun latestUpdatesParse(response: Response): MangasPage {
        val body = response.body!!.string()
        val url = response.request.url.toString()
        ProComicDiag.logResponse("LATEST", response, body)
        val series = ProComicUtils.extractSeriesList(body, "LATEST", url)
        val mangas = series.map { it.toSManga() }
        ProComicDiag.logStage("LATEST", 99,
            "MangasPage: ${mangas.size} items, hasNextPage=${mangas.size >= 20}")
        return MangasPage(mangas, hasNextPage = mangas.size >= 20)
    }
```

- [ ] **Step 10: Replace `searchMangaParse` (lines 146–160)**

```kotlin
    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body!!.string()
        val url = response.request.url.toString()
        ProComicDiag.logResponse("SEARCH", response, body)
        val series = ProComicUtils.extractSeriesList(body, "SEARCH", url)
        val query = response.request.url.fragment
            ?.removePrefix("q=")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
        ProComicDiag.logStage("SEARCH", 6,
            "query=${query.take(50)}, series before text-filter=${series.size}")
        val filtered = if (query.isBlank()) {
            series
        } else {
            series.filter { dto ->
                dto.title.contains(query, ignoreCase = true) ||
                dto.slug.replace("-", " ").contains(query, ignoreCase = true)
            }
        }
        val mangas = filtered.map { it.toSManga() }
        ProComicDiag.logStage("SEARCH", 99,
            "MangasPage: ${mangas.size} items after text-filter")
        return MangasPage(mangas, hasNextPage = mangas.size >= 20 && query.isBlank())
    }
```

- [ ] **Step 11: Replace `mangaDetailsParse` (lines 168–172)**

```kotlin
    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body!!.string()
        val url = response.request.url.toString()
        ProComicDiag.logResponse("DETAIL", response, body)
        return ProComicUtils.extractSeriesDetail(body, "DETAIL", url)?.toSManga()
            ?: throw Exception(
                "ProComic: could not parse series details from RSC response"
            )
    }
```

- [ ] **Step 12: Replace `chapterListParse` (lines 181–191)**

```kotlin
    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body!!.string()
        val url = response.request.url.toString()
        ProComicDiag.logResponse("CHAPTERS", response, body)
        val mangaUrl = response.request.url.toString()
            .removePrefix(baseUrl)
            .substringBefore("?")

        return ProComicUtils.extractChapterList(body, "CHAPTERS", url).map { dto ->
            dto.toSChapter(mangaUrl)
        }
    }
```

- [ ] **Step 13: Replace `pageListParse` (lines 201–215)**

```kotlin
    override fun pageListParse(response: Response): List<Page> {
        val body = response.body!!.string()
        val url = response.request.url.toString()
        ProComicDiag.logResponse("PAGES", response, body)
        val images = ProComicUtils.extractPageImages(body, "PAGES", url)
        ProComicDiag.logStage("PAGES", 99, "images found: ${images.size}")

        if (images.isEmpty()) {
            return emptyList()
        }
        return images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }
```

- [ ] **Step 14: Verify full project compiles**

```bash
cd '/home/lonevertex/Documents/Projects/ProComic Extension'
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 0 errors.

---

## Task 4 — Build, Install, Collect Logs

- [ ] **Step 15: Full debug build**

```bash
cd '/home/lonevertex/Documents/Projects/ProComic Extension'
./gradlew :app:assembleDebug 2>&1 | tail -20
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

Expected: `BUILD SUCCESSFUL`. Record file size.

- [ ] **Step 16: Verify device connected**

```bash
adb devices -l
adb shell getprop ro.product.model
```

Expected: 1 device, `SM-A217F`.

- [ ] **Step 17: Install APK**

```bash
adb install -r 'app/build/outputs/apk/debug/app-debug.apk'
```

Expected: `Success`.

- [ ] **Step 18: Clear logcat + start capture**

```bash
adb logcat -c
adb shell am force-stop app.mihon
sleep 2
adb logcat -s ProComicDiag > /tmp/procomic_diag.log &
LOGPID=$!
echo "Logcat PID: $LOGPID"
```

- [ ] **Step 19: Launch Mihon, trigger Popular then Latest**

```bash
adb shell monkey -p app.mihon -c android.intent.category.LAUNCHER 1
sleep 10
```

Then via uiautomator or manual tap: navigate to ProComic source → Popular tab → wait 15s → Latest tab → wait 15s.

- [ ] **Step 20: Stop capture, count lines**

```bash
kill $LOGPID
wc -l /tmp/procomic_diag.log
grep -c "ProComicDiag" /tmp/procomic_diag.log
cat /tmp/procomic_diag.log
```

---

## Task 5 — Analysis Checklist (9 gates)

Run each check against `/tmp/procomic_diag.log`. Every gate must be answered.

| Gate | Command | Expected | Failure means |
|------|---------|----------|---------------|
| A1: RSC:1 sent | `grep "req> RSC" /tmp/procomic_diag.log` | `RSC: 1` | Header not in request |
| A2: HTTP status | `grep "\[POPULAR\] Status:" /tmp/procomic_diag.log` | `200` | 403=WAF, 410=domain blocked, 3xx=redirect |
| A3: Content-Type | `grep "\[POPULAR\] Content-Type:" /tmp/procomic_diag.log` | `text/x-component` | HTML = server returned regular page |
| A4: Body length | `grep "Body length" /tmp/procomic_diag.log` | `~154770` | <1000 = short body, returns emptyList silently |
| A5: SHA-256 match | compare with `sha256sum /tmp/ondevice_rsc.txt` | match | Different = server sends different body to OkHttp |
| A6: initialSeries | `grep "\[POPULAR\]\[S2\]" /tmp/procomic_diag.log` | `FOUND at index=12217` | NOT FOUND = body differs |
| A7: decodeFromString | `grep "\[POPULAR\]\[S4\]" /tmp/procomic_diag.log` | `SUCCESS: 18 items` | EXCEPTION = root cause is deserialization |
| A8: exception detail | `grep "EXCEPTION\|stacktrace" /tmp/procomic_diag.log` | (none) | Shows exact exception class + message |
| A9: final count | `grep "\[POPULAR\]\[S99\]" /tmp/procomic_diag.log` | `MangasPage: 14 items` | 0 = failure confirmed upstream |

---

## Task 6 — Commit

- [ ] **Step 21: Commit after logs are captured and analyzed**

```bash
cd '/home/lonevertex/Documents/Projects/ProComic Extension'
git add \
  app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicDiag.kt \
  app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComicUtils.kt \
  app/src/main/kotlin/eu/kanade/tachiyomi/extension/ar/procomic/ProComic.kt
git commit -m "debug(diag): add runtime instrumentation for RSC parse pipeline

Add ProComicDiag singleton (SHA-256, HTTP response logger, parser stage
logger, verbose exception logger). Instrument all 5 parse methods and all
4 utility functions. Replace all silent catch blocks with logged versions
that emit full exception type, message, cause, and stack trace.

Filter with: adb logcat -s ProComicDiag

NO logic changes. NO domain changes. NO parser algorithm changes.
NO DTO changes. Additive instrumentation only.

Root-cause investigation for: 'No results found' on Popular/Latest.
Remove before publishing to keiyoushi repo."
```

---

## Self-Review

| Requirement | Covered by |
|-------------|-----------|
| URL logged | `logResponse` → `response.request.url` |
| HTTP status logged | `logResponse` → `response.code` |
| All response headers | `logResponse` → `response.headers.forEach` |
| All request headers | `logResponse` → `response.request.headers.forEach` |
| Content-Type, Content-Encoding | `logResponse` (explicit named fields) |
| Content-Length header | `logResponse` — compare vs `body.length` for gzip detection |
| Body length | `logResponse` → `body.length` |
| Body SHA-256 | `logResponse` → `sha256(body)` |
| First 500 chars | `logResponse` → `body.take(500)` |
| Last 200 chars | `logResponse` → `body.takeLast(200)` |
| `initialSeries` found/index | Stage S2 in `extractSeriesList` |
| Extracted JSON length + hash | Stage S3 |
| Deserialization start/result | Stage S4 |
| Decoded DTO count | Stage S4/S5 |
| All silent catches replaced | All 4 catch blocks → `logException` before `emptyList()` |
| No logic changes | Confirmed — all returns unchanged |
| No domain changes | Confirmed — `baseUrl` untouched |
| No parser changes | Confirmed — `extractJsonArray` untouched |
| No DTO changes | Confirmed — all DTO files untouched |
