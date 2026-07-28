package eu.kanade.tachiyomi.extension.ar.procomic

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
     */
    fun extractSeriesList(body: String): List<ProComicSeriesDto> {
        val seriesJson = extractJsonArrayAfterKey(body, "initialSeries") ?: return emptyList()
        return try {
            json.decodeFromString<List<ProComicSeriesDto>>(seriesJson)
                .filter { it.type != "novel" }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Extract series detail from a series detail RSC response.
     * On detail pages, a single series object (not array) is embedded as a prop.
     */
    fun extractSeriesDetail(body: String): ProComicSeriesDto? {
        // Try array extraction first (some detail pages include the full series object in a list)
        val listResult = extractSeriesList(body)
        if (listResult.isNotEmpty()) return listResult.first()

        // Fallback: find the first occurrence of "id":<int>,"title":"...","slug":"...","type":"..."
        // and extract the surrounding JSON object
        val idx = body.indexOf("\"id\":")
        if (idx < 0) return null

        // Find the opening brace of the object containing this id
        val start = body.lastIndexOf("{", idx).takeIf { it >= 0 } ?: return null
        val objJson = extractJsonObject(body, start) ?: return null

        return try {
            json.decodeFromString<ProComicSeriesDto>(objJson)
                .takeIf { it.type != "novel" }
        } catch (e: Exception) {
            null
        }
    }

    // ---- Chapter List Extraction ----

    /**
     * Extract the chapter list from a series detail RSC response.
     * Chapters appear in "chapters":[{...},...] or "initialChapters":[...] props.
     * Only returns chapters with language == "AR".
     */
    fun extractChapterList(body: String): List<ProComicChapterDto> {
        // Try "chapters" key first, then "initialChapters"
        val chaptersJson = extractJsonArrayAfterKey(body, "chapters")
            ?: extractJsonArrayAfterKey(body, "initialChapters")
            ?: return emptyList()

        return try {
            json.decodeFromString<List<ProComicChapterDto>>(chaptersJson)
                .filter { it.language == "AR" }
                .sortedByDescending { it.chapterNumber.toFloatOrNull() ?: 0f }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ---- Page Image Extraction ----

    /**
     * Extract page image URLs from a chapter reader RSC response.
     * Images appear as "images":["url1","url2",...] in the RSC stream.
     * CDN enforces the publicImageCount limit server-side — only the allowed
     * images are embedded in the RSC response for guest users.
     */
    fun extractPageImages(body: String): List<String> {
        val imagesJson = extractJsonArrayAfterKey(body, "images") ?: return emptyList()
        return try {
            val arr = json.parseToJsonElement(imagesJson)
            if (arr is JsonArray) {
                arr.mapNotNull { element ->
                    element.jsonPrimitive.takeIf { it.isString }?.content
                        ?.takeIf { it.startsWith("https://") }
                }
            } else emptyList()
        } catch (e: Exception) {
            // Fallback: regex for CDN URLs
            Regex(""""(https://cdn\d*\.procomic\.(?:pro|net)/[^"]+)"""")
                .findAll(body)
                .map { it.groupValues[1] }
                .toList()
        }
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
