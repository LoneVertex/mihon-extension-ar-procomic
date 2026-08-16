package eu.kanade.tachiyomi.extension.ar.procomic

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * RSC (React Server Components) wire-format parser for procomic.pro.
 *
 * Architecture confirmed by Stage 3C+5 live recon (2026-07-26/27):
 *
 * The RSC wire format is a streaming line-based format. Series data is embedded as
 * props in a React element tree, specifically as the "initialSeries" prop:
 *
 *   "$Le",null,{"initialSeries":[{"id":688,"title":"...","slug":"...","type":"manhua",...}],"total":18}
 *
 * Chapter data follows a similar pattern in the series detail RSC stream.
 * Image URLs for chapter pages appear in the "images" prop.
 *
 * Strategy: Extract JSON arrays/objects by targeted boundary search, then
 * deserialize using kotlinx.serialization. No full RSC parser needed.
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
     *
     * The data is in: "initialSeries":[{...},{...},...]
     * Field order confirmed: id, title, slug, description, type, status, thumbnail, ...
     *
     * Filters out type == "novel" (Tachiyomi cannot render prose).
     *
     * @param diagTag  Logcat stage tag (e.g. "POPULAR"). Empty string suppresses all logging.
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
                "extractSeriesList: bodyLen=${body.length}, " +
                "sha256=${ProComicDiag.sha256(body)}")
        }

        val keyPattern = "\"initialSeries\":["
        val keyIdx = body.indexOf(keyPattern)
        if (diag) {
            if (keyIdx >= 0) {
                ProComicDiag.logStage(diagTag, 2,
                    "\"initialSeries\":[ FOUND at index=$keyIdx")
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

    /**
     * Extract series detail from a series detail RSC response.
     * On detail pages, a single series object (not array) is embedded as a prop.
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

        // Try array extraction first (some detail pages include the full series object in a list).
        // Wrapped in try-catch because extractSeriesList throws when it sees a large RSC body
        // that doesn't have 'initialSeries' — which is EXPECTED for detail pages (they embed
        // 'initialChapters' instead). We must not propagate that throw here.
        val listResult = try {
            extractSeriesList(body, diagTag, diagUrl)
        } catch (e: Exception) {
            if (diag) ProComicDiag.logStage(diagTag, 2,
                "extractSeriesList threw (expected on detail pages): ${e.message?.take(80)}")
            emptyList()
        }
        if (listResult.isNotEmpty()) {
            if (diag) ProComicDiag.logStage(diagTag, 2,
                "array path: found ${listResult.size} items, returning first")
            return listResult.first()
        }
        if (diag) ProComicDiag.logStage(diagTag, 2,
            "array path empty — trying single-object fallback")

        // Fallback: find the first occurrence of "id":<int>,"title":"...","slug":"...","type":"..."
        // and extract the surrounding JSON object
        val idx = body.indexOf("\"id\":")
        if (idx < 0) {
            if (diag) ProComicDiag.logStage(diagTag, 3,
                "\"id\": NOT FOUND — returning null")
            return null
        }

        // Find the opening brace of the object containing this id
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

    // ---- Chapter List Extraction ----

    /**
     * Extract the chapter list from a series detail RSC response.
     * Chapters appear in "chapters":[{...},...] or "initialChapters":[...] props.
     * Only returns chapters with language == "AR".
     *
     * @param diagTag  Logcat stage tag (e.g. "CHAPTERS"). Empty string suppresses logging.
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

        val chapIdx = body.indexOf("\"chapters\":[")
        val initIdx = body.indexOf("\"initialChapters\":[")
        if (diag) {
            ProComicDiag.logStage(diagTag, 2, "\"chapters\":[ index=$chapIdx")
            ProComicDiag.logStage(diagTag, 2, "\"initialChapters\":[ index=$initIdx")
        }

        // Try "initialChapters" first (confirmed key on procomic.net detail RSC as of 2026-08-02),
        // then fall back to "chapters" for any future API changes.
        val chaptersJson = extractJsonArrayAfterKey(body, "initialChapters")
            ?: extractJsonArrayAfterKey(body, "chapters")
            ?: throw Exception(
                "ProComic: 'initialChapters'/'chapters' key not found in RSC response. " +
                "Site may have updated."
            )

        if (diag) ProComicDiag.logStage(diagTag, 3,
            "chaptersJson: len=${chaptersJson.length}")

        return try {
            if (diag) ProComicDiag.logStage(diagTag, 4,
                "decodeFromString<List<ProComicChapterDto>> START")
            val decoded = json.decodeFromString<List<ProComicChapterDto>>(chaptersJson)
            if (diag) ProComicDiag.logStage(diagTag, 4,
                "decodeFromString: ${decoded.size} total")

            val arOnly = decoded.filter { it.language == "AR" }
            if (diag) ProComicDiag.logStage(diagTag, 5,
                "AR filter: ${decoded.size} → ${arOnly.size}")

            val sorted = arOnly.sortedByDescending { it.chapterNumber.toFloatOrNull() ?: 0f }
            if (diag) ProComicDiag.logStage(diagTag, 6,
                "returning ${sorted.size} chapters")
            sorted
        } catch (e: Exception) {
            if (diag) ProComicDiag.logException(diagTag,
                "decodeFromString<List<ProComicChapterDto>>", diagUrl, e)
            emptyList()
        }
    }

    // ---- Page Image Extraction ----

    /**
     * Extract page image URLs from a chapter reader response.
     * Parses the embedded `appImages` manifest from the HTML/RSC body:
     *   "appImages":[{"mobile":"https://app.procomic.pro/...","desktop":"https://app.procomic.pro/..."}, ...]
     *
     * @param diagTag  Logcat stage tag (e.g. "PAGES"). Empty string suppresses logging.
     * @param diagUrl  Request URL for exception context.
     * @throws Exception if no valid chapter images are found.
     */
    fun extractPageImages(
        body: String,
        diagTag: String = "",
        diagUrl: String = "",
    ): List<String> {
        val diag = diagTag.isNotEmpty()
        if (diag) ProComicDiag.logStage(diagTag, 1, "extractPageImages: bodyLen=${body.length}")

        // 1. Find and extract the appImages JSON array
        val appImagesJson = extractJsonArrayAfterKey(body, "appImages")
        if (appImagesJson != null) {
            if (diag) ProComicDiag.logStage(diagTag, 2, "appImagesJson extracted: len=${appImagesJson.length}")
            try {
                val decoded = json.decodeFromString<List<ProComicAppImage>>(appImagesJson)
                val urls = decoded.mapNotNull { item ->
                    (item.desktop?.takeIf { it.isNotBlank() } ?: item.mobile?.takeIf { it.isNotBlank() })
                        ?.takeIf { it.startsWith("http") }
                }.distinct()

                // Filter to chapter-scoped CDN URLs if present
                val chapterUrls = urls.filter { url ->
                    url.contains("/chapters/")
                }

                val finalUrls = if (chapterUrls.isNotEmpty()) chapterUrls else urls
                if (finalUrls.isNotEmpty()) {
                    if (diag) ProComicDiag.logStage(diagTag, 3, "parsed ${finalUrls.size} images from appImages manifest")
                    return finalUrls
                }
            } catch (e: Exception) {
                if (diag) ProComicDiag.logException(diagTag, "decodeFromString<List<ProComicAppImage>>", diagUrl, e)
            }
        }

        // 2. Fallback: regex for chapter CDN image URLs directly in the page body
        val chapterRegex = Regex(""""(https://app\.procomic\.(pro|net)/chapters/[^"]+\.(avif|webp|jpg|jpeg|png))"""")
        val fallbackUrls = chapterRegex.findAll(body)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

        if (fallbackUrls.isNotEmpty()) {
            if (diag) ProComicDiag.logStage(diagTag, 4, "fallback regex found ${fallbackUrls.size} chapter images")
            return fallbackUrls
        }

        // 3. Explicit Failure
        val reason = when {
            body.isBlank() -> "Response body was empty (0 bytes)"
            !body.contains("appImages") -> "No 'appImages' manifest found in response"
            else -> "Failed to decode valid chapter images from 'appImages' manifest"
        }
        throw Exception("ProComic Reader: $reason (bodyLength=${body.length}, url=$diagUrl)")
    }

    // ---- JSON Extraction Utilities ----

    /**
     * Find the first occurrence of "key":[...] in [body] and return the JSON array string.
     * Uses a bracket-counting approach to find the matching closing bracket.
     */
    private fun extractJsonArrayAfterKey(body: String, key: String): String? {
        val keyPattern = "\"$key\":["
        val start = body.indexOf(keyPattern)
        if (start < 0) return null

        val arrStart = start + keyPattern.length - 1 // position of '['
        return extractJsonArray(body, arrStart)
    }

    /**
     * Starting at [startPos] (which must be '['), walk through the body counting
     * square brackets only (not curly braces) to find the matching ']'.
     * Returns the full JSON array string including the opening and closing brackets.
     *
     * String-aware: skips brackets inside quoted strings (handles JSON escape sequences).
     */
    private fun extractJsonArray(body: String, startPos: Int): String? {
        if (startPos >= body.length || body[startPos] != '[') return null

        var depth = 0
        var inString = false
        var escaped = false
        var pos = startPos

        while (pos < body.length) {
            val c = body[pos]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '[' -> depth++
                !inString && c == ']' -> {
                    depth--
                    if (depth == 0) return body.substring(startPos, pos + 1)
                }
            }
            pos++
        }
        return null
    }

    /**
     * Starting at [startPos] (which must be '{'), walk through the body to find
     * the matching '}' and return the full JSON object string.
     */
    private fun extractJsonObject(body: String, startPos: Int): String? {
        if (startPos >= body.length || body[startPos] != '{') return null

        var depth = 0
        var inString = false
        var escaped = false
        var pos = startPos

        while (pos < body.length) {
            val c = body[pos]
            if (escaped) {
                escaped = false
            } else when {
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return body.substring(startPos, pos + 1)
                }
            }
            pos++
        }
        return null
    }
}
