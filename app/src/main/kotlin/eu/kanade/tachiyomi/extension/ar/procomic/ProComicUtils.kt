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
        expectedId: Int? = null,
        expectedSlug: String? = null,
    ): ProComicDetailsResult? {
        val diag = diagTag.isNotEmpty()
        if (diag) ProComicDiag.logStage(diagTag, 1,
            "extractSeriesDetail: bodyLen=${body.length}")

        // Canonical detail RSC embeds a complete DTO as {"series":{...}}.
        // The value must be an object immediately after the key. A restricted
        // response uses {"series":null}; never scan forward into params or any
        // other unrelated object.
        val canonicalSeriesJson = extractJsonObjectAfterKey(body, "series")
        if (canonicalSeriesJson != null) {
            if (diag) ProComicDiag.logStage(diagTag, 2,
                "canonical \"series\" object found: len=${canonicalSeriesJson.length}")
            val decoded = decodeSeriesDetail(canonicalSeriesJson, diagTag, diagUrl)
            if (decoded != null && isStructurallyValidSeries(decoded, expectedId, expectedSlug)) {
                return ProComicDetailsResult.Complete(decoded)
            }
            if (diag) ProComicDiag.logStage(diagTag, 3,
                "canonical series candidate rejected by structural identity validation")
        }

        val restricted = extractRestrictedDetails(body, expectedId, expectedSlug, diagTag)
        if (restricted != null) {
            if (diag) ProComicDiag.logStage(diagTag, 3,
                "restricted series payload recognized: id=${restricted.id}, slug=${restricted.slug}")
            return ProComicDetailsResult.Restricted(restricted)
        }

        // Try array extraction only after the canonical and restricted paths.
        // Detail pages may not contain initialSeries; that absence is expected.
        val listResult = try {
            extractSeriesList(body, diagTag, diagUrl)
        } catch (e: Exception) {
            if (diag) ProComicDiag.logStage(diagTag, 2,
                "extractSeriesList threw (expected on detail pages): ${e.message?.take(80)}")
            emptyList()
        }
        val matchingListItem = listResult.firstOrNull {
            isStructurallyValidSeries(it, expectedId, expectedSlug)
        }
        if (matchingListItem != null) {
            if (diag) ProComicDiag.logStage(diagTag, 2,
                "array path: selected structurally valid id=${matchingListItem.id}")
            return ProComicDetailsResult.Complete(matchingListItem)
        }
        if (diag) ProComicDiag.logStage(diagTag, 2,
            "no structurally valid Details series candidate found")
        return null
    }

    private fun decodeSeriesDetail(
        seriesJson: String,
        diagTag: String,
        diagUrl: String,
    ): ProComicSeriesDto? {
        return try {
            if (diagTag.isNotEmpty()) ProComicDiag.logStage(diagTag, 4,
                "decodeFromString<ProComicSeriesDto> START")
            val dto = json.decodeFromString<ProComicSeriesDto>(seriesJson)
                .takeIf { it.type != "novel" }
            if (diagTag.isNotEmpty()) ProComicDiag.logStage(diagTag, 4,
                "decodeFromString result: id=${dto?.id}, type=${dto?.type}")
            dto
        } catch (e: Exception) {
            if (diagTag.isNotEmpty()) ProComicDiag.logException(diagTag,
                "decodeFromString<ProComicSeriesDto>", diagUrl, e)
            null
        }
    }

    private fun isStructurallyValidSeries(
        series: ProComicSeriesDto,
        expectedId: Int?,
        expectedSlug: String?,
    ): Boolean {
        if (expectedId != null && series.id != expectedId) return false
        if (series.title.isBlank() || series.slug.isBlank() || series.type.isBlank()) return false
        if (expectedSlug != null && series.slug != expectedSlug) return false
        return true
    }

    private fun extractRestrictedDetails(
        body: String,
        expectedId: Int?,
        expectedSlug: String?,
        diagTag: String,
    ): ProComicRestrictedDetails? {
        val restricted = extractBooleanAfterKey(body, "restricted") ?: return null
        if (!restricted) return null

        val paramsJson = extractJsonObjectAfterKey(body, "params") ?: return null
        val params = runCatching { json.decodeFromString<ProComicRestrictedParams>(paramsJson) }
            .getOrNull() ?: return null
        val id = params.id.toIntOrNull() ?: return null
        if (expectedId != null && id != expectedId) return null
        if (params.slug.isBlank() || params.type.isBlank()) return null
        if (expectedSlug != null && params.slug != expectedSlug) return null

        val title = extractRestrictedTitle(body) ?: return null
        val summary = extractRestrictedSummary(body, id)
        if (diagTag.isNotEmpty()) {
            ProComicDiag.logStage(
                diagTag,
                3,
                "restricted metadata: id=$id, hasSummary=${summary != null}, bodyLen=${body.length}",
            )
        }
        return ProComicRestrictedDetails(
            id = id,
            title = title,
            type = params.type,
            slug = params.slug,
            restricted = true,
            coverImage = summary?.coverImage,
            description = summary?.description,
            totalChapters = summary?.totalChapters,
            latestChapterNumber = summary?.latestChapterNumber,
            latestChapterDate = summary?.latestChapterDate,
            readHref = summary?.readHref,
            readIsExternal = summary?.readIsExternal,
            originalSources = summary?.originalSources ?: emptyList(),
        )
    }

    private fun extractRestrictedTitle(body: String): String? {
        val marker = "صفحة معلومات "
        val start = body.indexOf(marker)
        if (start < 0) return null
        val titleStart = start + marker.length
        val titleEnd = body.indexOf(':', titleStart)
        if (titleEnd <= titleStart) return null
        return body.substring(titleStart, titleEnd).trim().takeIf { it.isNotBlank() }
    }

    private fun extractRestrictedSummary(body: String, expectedId: Int): ProComicRestrictedSummary? {
        val markerIndex = body.indexOf("\"coverImage\":")
        if (markerIndex < 0) return null
        val segment = body.substring(markerIndex, minOf(body.length, markerIndex + 900))
        return ProComicRestrictedSummary(
            coverImage = extractStringAfterKey(segment, "coverImage"),
            description = extractStringAfterKey(segment, "description"),
            totalChapters = extractIntAfterKey(segment, "totalChapters"),
            latestChapterNumber = extractStringAfterKey(segment, "latestChapterNumber"),
            latestChapterDate = extractStringAfterKey(segment, "latestChapterDate"),
            readHref = extractStringAfterKey(segment, "readHref"),
            readIsExternal = extractBooleanAfterKey(segment, "readIsExternal"),
            originalSources = extractStringArrayAfterKey(segment, "originalSources"),
        ).takeIf {
            it.totalChapters != null ||
                it.latestChapterNumber != null ||
                it.latestChapterDate != null ||
                it.readHref != null
        }
    }

    private fun extractBooleanAfterKey(body: String, key: String): Boolean? {
        val marker = "\"$key\":"
        val keyIndex = body.indexOf(marker)
        if (keyIndex < 0) return null
        val valueStart = skipJsonWhitespace(body, keyIndex + marker.length)
        return when {
            body.startsWith("true", valueStart) -> true
            body.startsWith("false", valueStart) -> false
            else -> null
        }
    }

    private fun extractIntAfterKey(body: String, key: String): Int? {
        val marker = "\"$key\":"
        val keyIndex = body.indexOf(marker)
        if (keyIndex < 0) return null
        val valueStart = skipJsonWhitespace(body, keyIndex + marker.length)
        val end = body.indexOfFirstFrom(valueStart) { it == ',' || it == '}' }
        return body.substring(valueStart, end).trim().toIntOrNull()
    }

    private fun extractStringAfterKey(body: String, key: String): String? {
        val marker = "\"$key\":"
        val keyIndex = body.indexOf(marker)
        if (keyIndex < 0) return null
        val valueStart = skipJsonWhitespace(body, keyIndex + marker.length)
        if (valueStart >= body.length || body[valueStart] != '"') return null
        val end = findJsonStringEnd(body, valueStart) ?: return null
        return body.substring(valueStart + 1, end)
    }

    private fun extractStringArrayAfterKey(body: String, key: String): List<String> {
        val marker = "\"$key\":"
        val keyIndex = body.indexOf(marker)
        if (keyIndex < 0) return emptyList()
        val valueStart = skipJsonWhitespace(body, keyIndex + marker.length)
        if (valueStart >= body.length || body[valueStart] != '[') return emptyList()
        val end = body.indexOf(']', valueStart)
        if (end < 0) return emptyList()
        return Regex("\\\"([^\\\"]*)\\\"").findAll(body.substring(valueStart, end + 1))
            .map { it.groupValues[1] }
            .toList()
    }

    private fun skipJsonWhitespace(body: String, start: Int): Int {
        var index = start
        while (index < body.length && body[index].isWhitespace()) index++
        return index
    }

    private fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
        for (index in start until length) {
            if (predicate(this[index])) return index
        }
        return length
    }

    private fun findJsonStringEnd(body: String, start: Int): Int? {
        var escaped = false
        for (index in (start + 1) until body.length) {
            val char = body[index]
            if (escaped) {
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                return index
            }
        }
        return null
    }

    // ---- Chapter Normalization ----

    /**
     * Classify verified chapter gate metadata conservatively.
     *
     * Access restriction is intentionally not represented by these fields. Callers must use
     * [ProComicGateState.RESTRICTED_AUTH_REQUIRED] only when a separate restricted-content
     * contract explicitly identifies it; an access denial alone is never a paid signal.
     */
    fun classifyGateState(gate: ProComicChapterGate): ProComicGateState {
        val lockedForever = gate.lockedForever == true
        val lockedByCoins = gate.lockedByCoins == true
        val lockedByExclusive = gate.lockedByExclusive == true
        val hasShortlink = gate.hasShortlink == true
        val hasCoinCost = (gate.coinsRequired ?: 0) > 0 || gate.coinsUnlocks > 0
        val hasShortlinkCost = gate.shortlinkUnlocks > 0
        val allLockFlagsExplicitlyFalse = listOf(
            gate.lockedForever,
            gate.lockedByCoins,
            gate.lockedByExclusive,
            gate.hasShortlink,
        ).all { it == false }

        if (lockedForever) return ProComicGateState.PERMANENTLY_LOCKED
        if (lockedByCoins && lockedByExclusive) return ProComicGateState.UNKNOWN
        if (lockedByCoins) {
            return if (gate.coinsRequired != null && gate.coinsRequired > 0) {
                ProComicGateState.COIN_LOCKED
            } else {
                ProComicGateState.UNKNOWN
            }
        }
        if (lockedByExclusive) return ProComicGateState.EXCLUSIVE
        if (hasShortlink) return ProComicGateState.SHORTLINK_UNLOCK

        // A positive cost without its corresponding explicit lock/unlock mechanism is incomplete.
        if (hasCoinCost || hasShortlinkCost) return ProComicGateState.UNKNOWN
        if (!allLockFlagsExplicitlyFalse) return ProComicGateState.UNKNOWN

        return ProComicGateState.FREE
    }

    /**
     * Apply the Arabic-source chapter policy before creating Mihon SChapter models.
     *
     * The REST API returns distinct AR/EN records for the same numeric chapter. This
     * pipeline selects AR when available, keeps EN only as a visible fallback, preserves
     * verified gate metadata, deduplicates after language selection, and sorts the final
     * records deterministically.
     */
    fun normalizeChapters(
        chapters: List<ProComicChapterDto>,
        diagTag: String = "",
        diagUrl: String = "",
    ): List<ProComicNormalizedChapter> {
        val normalized = chapters.map { chapter ->
            ProComicNormalizedChapter(
                source = chapter,
                languageCode = normalizeLanguageCode(chapter.language),
                languageDisplay = languageDisplayName(chapter.language),
                numericNumber = chapter.chapterNumber.toFloatOrNull(),
                gate = chapter.toGateState(),
            )
        }

        val selected = normalized
            .groupBy { chapterIdentity(it) }
            .values
            .mapNotNull { group ->
                val arabic = group.filter { it.languageCode == "AR" }
                val english = group.filter { it.languageCode == "EN" }
                when {
                    arabic.isNotEmpty() -> chooseStable(arabic, englishFallback = false)
                    english.isNotEmpty() -> chooseStable(english, englishFallback = true)
                    else -> chooseStable(group, englishFallback = false)
                }
            }
            .sortedWith(normalizedChapterComparator)

        if (diagTag.isNotEmpty()) {
            ProComicDiag.logStage(
                diagTag,
                7,
                "normalizeChapters: input=${chapters.size}, output=${selected.size}, url=$diagUrl",
            )
        }
        return selected
    }

    private fun normalizeLanguageCode(language: String): String =
        language.trim().uppercase().ifBlank { "UNKNOWN" }

    private fun languageDisplayName(language: String): String {
        return when (val code = normalizeLanguageCode(language)) {
            "AR" -> "Arabic"
            "EN" -> "English"
            else -> code
        }
    }

    private fun chapterIdentity(chapter: ProComicNormalizedChapter): String {
        val numeric = chapter.numericNumber
        return if (numeric != null) {
            "numeric:${numeric.toString()}"
        } else {
            "special:${chapter.source.chapterNumber.trim().lowercase()}"
        }
    }

    private fun chooseStable(
        records: List<ProComicNormalizedChapter>,
        englishFallback: Boolean,
    ): ProComicNormalizedChapter {
        val selected = records.maxWithOrNull(
            compareBy<ProComicNormalizedChapter> {
                it.source.publishedAt ?: it.source.createdAt ?: ""
            }.thenBy { it.source.id },
        ) ?: error("ProComic: empty normalized chapter group")
        return selected.copy(isEnglishFallback = englishFallback)
    }

    private val normalizedChapterComparator = Comparator<ProComicNormalizedChapter> { left, right ->
        val leftNumber = left.numericNumber
        val rightNumber = right.numericNumber
        val numberComparison = when {
            leftNumber != null && rightNumber == null -> -1
            leftNumber == null && rightNumber != null -> 1
            leftNumber != null && rightNumber != null -> rightNumber.compareTo(leftNumber)
            else -> left.source.chapterNumber.compareTo(right.source.chapterNumber)
        }
        if (numberComparison != 0) return@Comparator numberComparison

        val timestampComparison = (right.source.publishedAt ?: right.source.createdAt ?: "")
            .compareTo(left.source.publishedAt ?: left.source.createdAt ?: "")
        if (timestampComparison != 0) return@Comparator timestampComparison
        right.source.id.compareTo(left.source.id)
    }

    private fun ProComicChapterDto.toGateState(): ProComicChapterGate =
        ProComicChapterGate(
            supportMode = supportMode,
            coinsUnlocks = coinsUnlocks,
            shortlinkUnlocks = shortlinkUnlocks,
            coinsRequired = coinsRequired,
            hasShortlink = hasShortlink,
            lockedForever = lockedForever,
            lockedByCoins = lockedByCoins,
            lockedByExclusive = lockedByExclusive,
            publicImageCount = metadata?.protection?.publicImageCount,
        )

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
     * Find the first occurrence of `"key":{...}` in [body] and return the JSON object.
     * The canonical Details response embeds the DTO under `"series"`.
     */
    private fun extractJsonObjectAfterKey(body: String, key: String): String? {
        val keyPattern = "\"$key\":"
        val keyIndex = body.indexOf(keyPattern)
        if (keyIndex < 0) return null
        val valueStart = skipJsonWhitespace(body, keyIndex + keyPattern.length)
        if (valueStart >= body.length || body[valueStart] != '{') return null
        return extractJsonObject(body, valueStart)
    }

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
