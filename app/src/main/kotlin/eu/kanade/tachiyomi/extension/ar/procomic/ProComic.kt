package eu.kanade.tachiyomi.extension.ar.procomic

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.net.URLEncoder

/**
 * ProComic Tachiyomi/Mihon Extension
 *
 * Source: procomic.net (Arabic manga/manhwa aggregator)
 * Platform: Custom Next.js App Router (RSC streaming), internal brand "ProChan"
 * Domain history: prochan.net → procomic.net → procomic.pro → procomic.net
 *   procomic.pro now returns HTTP 410 for all series detail pages; canonical domain
 *   reverted to procomic.net (confirmed 2026-08-02 via live RSC probe).
 *
 * Architecture: HttpSource with mixed RSC (React Server Components) and public JSON API
 *   contracts. Search, Chapters, Popular, and Latest use verified public JSON endpoints; Details
 *   uses canonical RSC; Reader uses raw HTML with an embedded page-image manifest.
 *   RSC requests use the RSC: 1 header and ?_rsc= query parameter and return text/x-component
 *   data containing embedded JSON fragments. No browser-only DOM scraping is used by the parser.
 *
 * Known limitations:
 *   - Reader responses expose a public image prefix and may defer additional pages through the
 *     site's public web deferred-media/proxy-plan flow. Server-side security-gated chapters remain
 *     unavailable without the site's own allowed verification path; no login or bypass is used.
 *   - Novel-type content (light novels) is excluded — Tachiyomi cannot render prose text.
 *   - Search and sort query parameters were confirmed during recon but may shift if the site
 *     updates its routing. Monitor for HTTP 404 or empty RSC responses.
 *
 * Evidence base: docs/research/procomic-recon.md (Stage 3C recon report, 2026-07-26)
 */
class ProComic : HttpSource(), ConfigurableSource {

    private companion object {
        const val PREF_SHOW_PAID_CHAPTERS = "show_paid_chapters"
        val PAID_GATE_STATES = setOf(
            ProComicGateState.COIN_LOCKED,
            ProComicGateState.EXCLUSIVE,
            ProComicGateState.SHORTLINK_UNLOCK,
            ProComicGateState.PERMANENTLY_LOCKED,
        )
        const val MAX_RESPONSE_BYTES = 2_000_000
        const val MAX_CHAPTER_PAGES = 50
        const val MAX_READER_DEFERRED_MAPS = 50
        const val SEARCH_PAGE_LIMIT = 50
        const val MAX_SEARCH_PAGES_PER_BATCH = 6
        const val READER_BASE_URL = "https://procomic.pro"
        val SEARCH_TOKEN_REGEX = Regex("[\\p{L}\\p{N}]+")
    }

    private fun readBoundedBody(response: Response): String {
        val body = response.body ?: throw Exception("ProComic: response body is missing")
        val declaredLength = body.contentLength()
        if (declaredLength > MAX_RESPONSE_BYTES) {
            throw Exception("ProComic: response exceeds ${MAX_RESPONSE_BYTES} bytes")
        }
        val source = body.source()
        val buffer = Buffer()
        while (buffer.size <= MAX_RESPONSE_BYTES) {
            val remaining = MAX_RESPONSE_BYTES.toLong() + 1L - buffer.size
            val read = source.read(buffer, minOf(remaining, 16_384L))
            if (read == -1L) break
            if (buffer.size > MAX_RESPONSE_BYTES) {
                throw Exception("ProComic: response exceeds ${MAX_RESPONSE_BYTES} bytes")
            }
        }
        return buffer.readByteArray().toString(StandardCharsets.UTF_8)
    }

    private fun chapterPageFingerprint(data: ProComicChapterListResponse): String =
        data.chapters.joinToString(",") { it.id.toString() }

    // Initialized when Mihon builds the source preference screen. Until then, preserve the
    // historical behavior of showing every normalized chapter.
    private var sourcePreferences: SharedPreferences? = null

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(ProComicImageInterceptor(network.client))
        .build()

    override val name = "ProComic"
    override val baseUrl = "https://procomic.net"
    override val lang = "ar"
    override val supportsLatest = true

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        sourcePreferences = screen.context.applicationContext.getSharedPreferences("source_$id", 0)
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_PAID_CHAPTERS
            title = "عرض الفصول المدفوعة"
            summary = "إظهار جميع الفصول. عند التعطيل، تُخفى الفصول المحددة بوضوح كمقفلة أو مدفوعة فقط."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private fun shouldShowPaidChapters(): Boolean =
        sourcePreferences?.getBoolean(PREF_SHOW_PAID_CHAPTERS, true) ?: true

    /** Map only publication-lifecycle values; access visibility is not a lifecycle status. */
    private fun mapPublicationStatus(progress: String?, lifecycleStatus: String?): Int {
        return sequenceOf(progress, lifecycleStatus)
            .mapNotNull { value ->
                when (value?.trim()?.lowercase()) {
                    "ongoing", "مستمر" -> SManga.ONGOING
                    "completed", "complete", "finished", "مكتمل" -> SManga.COMPLETED
                    "hiatus", "on hiatus", "متوقف مؤقتا", "متوقف مؤقتًا" -> SManga.ON_HIATUS
                    "dropped", "cancelled", "canceled", "متوقف" -> SManga.CANCELLED
                    else -> null
                }
            }
            .firstOrNull() ?: SManga.UNKNOWN
    }

    // Mobile Chrome UA is required — plain curl UA gets same response, but this
    // matches what a real Tachiyomi+WebView session would present.
    private val mobileUserAgent =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * Base headers for ALL requests — including WebView navigation.
     * RSC-specific headers are intentionally excluded here so that WebView
     * receives a normal text/html response instead of the RSC wire format.
     *
     * EVIDENCE (Probe P05, 2026-08-01): Without RSC:1, server returns
     * text/html (282651B). With RSC:1, server returns text/x-component (169177B).
     * User screenshot confirmed WebView displayed raw RSC: '1:"$Sreact.fragment"'.
     */
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", mobileUserAgent)
        .add("Accept-Language", "ar,en;q=0.9")
        .add("Referer", baseUrl)

    /**
     * Headers for RSC data requests ONLY — never used for WebView navigation.
     *
     * RSC:1 is the sole required header to trigger text/x-component responses.
     * Next-Router-State-Tree is included for spec conformance but confirmed
     * optional: RSC:1 alone yields a full initialSeries RSC payload.
     * (Probe P05: RSC:1 only → 169177B with initialSeries; NRST only → HTML.)
     */
    private fun rscHeaders(): Headers = headersBuilder()
        .add("RSC", "1")
        .add("Next-Router-State-Tree", "%5B%22%22%2C%7B%7D%5D")
        .build()

    // ---- Popular ----
    // Verified public contract: /api/public/content/popular-new?limit=20 returns
    // {success, data:[{content:{...}, viewCount:"..."}]}. page/offset/cursor values
    // are ignored by the observed endpoint and no continuation metadata is returned.
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/public/content/popular-new?limit=20", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val body = readBoundedBody(response)
        val url = response.request.url.toString()
        ProComicDiag.logResponse("POPULAR", response, body)
        val feed = ProComicUtils.json.decodeFromString<ProComicPopularResponse>(body)
        if (!feed.success) throw Exception("ProComic: Popular API returned success=false")

        val seenIds = HashSet<Int>()
        val content = feed.data.asSequence()
            .map { it.content }
            .filter { it.type != "novel" }
            .filter { seenIds.add(it.id) }
            .toList()
        val mangas = content.map { it.toPopularSManga() }

        ProComicDiag.logStage(
            "POPULAR",
            99,
            "API data=${feed.data.size}, nonNovelUnique=${mangas.size}, hasNextPage=false, url=$url",
        )
        // The API exposes no authoritative continuation signal; do not fabricate page 2.
        return MangasPage(mangas, hasNextPage = false)
    }

    // ---- Latest Updates ----
    // Verified public contract: /api/public/content/latest-updates?limit=18&category=all&page=N.
    // Page values are authoritative, pages are server-ordered and disjoint in captures, and
    // an empty data array is the only observed termination signal. Short pages continue.
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/api/public/content/latest-updates?limit=18&category=all&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val body = readBoundedBody(response)
        val url = response.request.url.toString()
        ProComicDiag.logResponse("LATEST", response, body)
        val feed = ProComicUtils.json.decodeFromString<ProComicLatestResponse>(body)
        if (!feed.success) throw Exception("ProComic: Latest API returned success=false")

        val seenIds = HashSet<Int>()
        val series = feed.data.asSequence()
            .filter { it.type != "novel" }
            .filter { seenIds.add(it.mangaId) }
            .toList()
        val mangas = series.map { it.toLatestSManga() }
        val hasNextPage = feed.data.isNotEmpty()

        ProComicDiag.logStage(
            "LATEST",
            99,
            "API data=${feed.data.size}, nonNovelUnique=${mangas.size}, hasNextPage=$hasNextPage, url=$url",
        )
        return MangasPage(mangas, hasNextPage = hasNextPage)
    }


    // ---- Search ----
    //
    // EVIDENCE: Stage 6 browser network investigation confirmed that procomic.net uses
    // a dedicated server-side search REST endpoint:
    // GET /api/public/series/search?status=approved&limit=50&page={page}&sort=latest&search={query}
    //
    // Type filter is supported server-side via &type={manga|manhwa|manhua}.
    //
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = buildString {
            append(baseUrl)
            append("/api/public/series/search?status=approved&limit=$SEARCH_PAGE_LIMIT&page=$page&sort=latest")

            val effectiveQuery = query.trim().ifBlank { "a" }
            append("&search=")
            append(java.net.URLEncoder.encode(effectiveQuery, "UTF-8"))

            filters.forEach { filter ->
                when (filter) {
                    is TypeFilter -> {
                        if (filter.state > 0) {
                            append("&type=")
                            append(filter.typeValues[filter.state])
                        }
                    }
                    else -> {}
                }
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = readBoundedBody(response)
        val url = response.request.url.toString()
        ProComicDiag.logResponse("SEARCH", response, body)

        val requestedQuery = response.request.url.queryParameter("search")
            ?.takeUnless { it.equals("a", ignoreCase = true) }
            .orEmpty()
        var parsed = parseSearchResponse(body, requestedQuery)
        val mangas = parsed.mangas.toMutableList()
        val seenFingerprints = hashSetOf(searchPageFingerprint(parsed.response))
        var currentPage = parsed.response.meta?.page
            ?: response.request.url.queryParameter("page")?.toIntOrNull()
            ?: 1
        var pagesFetched = 1
        var exhausted = parsed.response.data.isEmpty() || parsed.response.data.size < SEARCH_PAGE_LIMIT

        // The endpoint’s page metadata drifts while the dataset changes, and local relevance
        // filtering can make an otherwise non-empty server page empty. Consume a bounded batch
        // inside this parse call so Mihon never receives an empty continuation page after valid
        // results. Stop on a short/empty page or repeated body; never follow an unbounded cursor.
        while (!exhausted && pagesFetched < MAX_SEARCH_PAGES_PER_BATCH) {
            val nextPage = currentPage + 1
            val nextRequest = response.request.newBuilder()
                .url(response.request.url.newBuilder().setQueryParameter("page", nextPage.toString()).build())
                .build()
            val nextParsed = client.newCall(nextRequest).execute().use { nextResponse ->
                val nextBody = readBoundedBody(nextResponse)
                ProComicDiag.logResponse("SEARCH_BATCH", nextResponse, nextBody)
                parseSearchResponse(nextBody, requestedQuery)
            }
            val fingerprint = searchPageFingerprint(nextParsed.response)
            if (!seenFingerprints.add(fingerprint)) {
                ProComicDiag.logStage("SEARCH", 98, "batch stopped on repeated page fingerprint")
                exhausted = true
                break
            }
            mangas += nextParsed.mangas
            parsed = nextParsed
            currentPage = nextParsed.response.meta?.page ?: nextPage
            pagesFetched++
            exhausted = nextParsed.response.data.isEmpty() || nextParsed.response.data.size < SEARCH_PAGE_LIMIT
        }

        val uniqueMangas = mangas.distinctBy { it.searchIdentity() }
        ProComicDiag.logStage("SEARCH", 99,
            "MangasPage: ${uniqueMangas.size} items, hasNextPage=false (batchPages=$pagesFetched, exhausted=$exhausted)")
        return MangasPage(uniqueMangas, hasNextPage = false)
    }

    private data class ParsedSearchResponse(
        val response: ProComicSearchResponse,
        val mangas: List<SManga>,
    )

    private fun parseSearchResponse(body: String, query: String): ParsedSearchResponse {
        val parsed = ProComicUtils.json.decodeFromString<ProComicSearchResponse>(body)
        val mangas = parsed.data
            .filter { it.type != "novel" }
            .filter { it.matchesSearchQuery(query) }
            .sortedByDescending { it.searchMatchQuality(query) }
            .map { it.toSManga() }
        return ParsedSearchResponse(parsed, mangas)
    }

    private fun searchPageFingerprint(response: ProComicSearchResponse): String =
        response.data.joinToString(",") { it.id.toString() }

    private fun ProComicSeriesDto.matchesSearchQuery(query: String): Boolean =
        searchMatchQuality(query) > 0

    /**
     * Rank visible-title matches above original-title/alias matches, and aliases above slug-only
     * matches. Descriptions are deliberately excluded because they are narrative text, not a
     * searchable identity field.
     */
    private fun ProComicSeriesDto.searchMatchQuality(query: String): Int {
        val queryTokens = searchTokens(query)
        if (queryTokens.isEmpty()) return 1

        val titleMatches = queryTokens.all { it in searchTokens(title) }
        if (titleMatches) return 3

        val originalTitleMatches = queryTokens.all { it in searchTokens(metadata?.originalTitle.orEmpty()) }
        val aliasMatches = metadata?.altTitles.orEmpty().any { alias ->
            queryTokens.all { it in searchTokens(alias) }
        }
        if (originalTitleMatches || aliasMatches) return 2

        return if (queryTokens.all { it in searchTokens(slug) }) 1 else 0
    }

    private fun SManga.searchIdentity(): String {
        val slug = url.trim('/').split('/').lastOrNull().orEmpty()
        return "${title.trim().lowercase()}|${slug.lowercase()}"
    }

    private fun searchTokens(value: String): Set<String> =
        SEARCH_TOKEN_REGEX.findAll(value.lowercase())
            .map { it.value }
            .filter { it.length > 1 }
            .toSet()

    // ---- Series Detail ----
    // manga.url remains "/ar/series/{type}/{id}/{slug}" for chapter REST parsing.
    // The live canonical Details route is "/ar/series/{slug}-{id}".
    private data class RestrictedMangaFallback(
        val thumbnailUrl: String?,
        val description: String?,
        val author: String?,
        val genre: String?,
        val status: Int,
    )

    override fun mangaDetailsRequest(manga: SManga): Request {
        val detailsPath = canonicalDetailsPath(manga.url)
        val fallback = RestrictedMangaFallback(
            thumbnailUrl = manga.thumbnail_url,
            description = manga.description,
            author = manga.author,
            genre = manga.genre,
            status = manga.status,
        )
        return GET("$baseUrl$detailsPath?_rsc=det", rscHeaders())
            .newBuilder()
            .tag(RestrictedMangaFallback::class.java, fallback)
            .build()
    }

    private fun canonicalDetailsPath(mangaUrl: String): String {
        val parts = mangaUrl.trim('/').split('/')
        if (parts.size >= 5 && parts[0] == "ar" && parts[1] == "series") {
            val id = parts[3]
            val slug = parts[4]
            if (id.isNotBlank() && slug.isNotBlank()) {
                return "/ar/series/$slug-$id"
            }
        }
        return mangaUrl
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val body = readBoundedBody(response)
        val url = response.request.url.toString()
        ProComicDiag.logResponse("DETAIL", response, body)
        val (expectedId, expectedSlug) = detailsIdentity(url)
        val restrictedFallback = response.request.tag(RestrictedMangaFallback::class.java)
        return when (val result = ProComicUtils.extractSeriesDetail(
            body,
            diagTag = "DETAIL",
            diagUrl = url,
            expectedId = expectedId,
            expectedSlug = expectedSlug,
        )) {
            is ProComicDetailsResult.Complete -> result.series.toSManga()
            is ProComicDetailsResult.Restricted -> result.details.toSManga(restrictedFallback)
            null -> throw Exception("ProComic: could not parse series details from RSC response")
        }
    }

    private fun ProComicRestrictedDetails.toSManga(
        fallback: RestrictedMangaFallback?,
    ): SManga = SManga.create().apply {
        url = "/ar/series/$type/$id/$slug"
        title = this@toSManga.title
        thumbnail_url = coverImage?.takeIf { it.startsWith("http") }
            ?: fallback?.thumbnailUrl
        description = description?.takeIf { it.isNotBlank() }
            ?: fallback?.description?.takeIf { it.isNotBlank() }
        author = fallback?.author?.takeIf { it.isNotBlank() }
        genre = fallback?.genre?.takeIf { it.isNotBlank() }
            ?: type.replaceFirstChar { it.uppercase() }
        status = fallback?.status ?: SManga.UNKNOWN
    }

    private fun detailsIdentity(url: String): Pair<Int?, String?> {
        val match = Regex("/ar/series/(.+)-(\\d+)(?:\\?.*)?$").find(url)
            ?: return null to null
        return match.groupValues[2].toIntOrNull() to match.groupValues[1]
    }

    // ---- Chapter List ----
    // ARCHITECTURE NOTE (2026-08-03):
    // Two chapter delivery mechanisms exist on procomic.net:
    //
    // A) New-style series (approx id >= 686):
    //    URL: /ar/series/{type}/{id}/{slug}   RSC has "initialChapters":[...]
    //
    // B) Old-style series (approx id < 686):
    //    URL: /ar/series/{type}/{id}/{slug}   → RSC REDIRECT to /ar/{slug}-{id}
    //    The redirect target RSC has NO initialChapters. Chapters are at REST API.
    //
    // REST API solution: GET /api/chapters?contentId={seriesId}
    //   • Returns {chapters:[...ProComicChapterDto...], total:N, hasMore:bool}
    //   • Page size = 20; pagination via &page=N
    //   • Works for ALL series (old and new style)
    //   • Confirmed for series 676 (34 chaps, hasMore=true) and 690 (4 chaps)
    //
    // manga.url is embedded as &_u= so chapterListParse can build chapter URLs.
    override fun chapterListRequest(manga: SManga): Request {
        val seriesId = manga.url.split("/").getOrNull(4) ?: ""
        val encodedMangaUrl = java.net.URLEncoder.encode(manga.url, "UTF-8")
        return GET(
            "$baseUrl/api/chapters?contentId=$seriesId&_u=$encodedMangaUrl",
            headers, // REST API — no RSC headers
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url
        val mangaUrl = url.queryParameter("_u")
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
        val contentId = url.queryParameter("contentId") ?: ""

        // Parse first page
        val firstPage = ProComicUtils.json.decodeFromString<ProComicChapterListResponse>(
            readBoundedBody(response)
        )
        ProComicDiag.logStage("CHAPTERS", 1,
            "REST API: total=${firstPage.total}, page1=${firstPage.chapters.size}, " +
            "hasMore=${firstPage.hasMore}")

        val all = firstPage.chapters.toMutableList()
        val seenPageFingerprints = hashSetOf(chapterPageFingerprint(firstPage))

        // Fetch remaining pages while hasMore == true. Stop on a repeated or empty page even if
        // the server incorrectly keeps hasMore=true, and retain the hard upper bound as a final guard.
        var page = 2
        var hasMore = firstPage.hasMore
        while (hasMore) {
            val nextData = client.newCall(
                GET("$baseUrl/api/chapters?contentId=$contentId&page=$page", headers)
            ).execute().use { next ->
                ProComicUtils.json.decodeFromString<ProComicChapterListResponse>(
                    readBoundedBody(next)
                )
            }
            val fingerprint = chapterPageFingerprint(nextData)
            ProComicDiag.logStage("CHAPTERS", page,
                "page=$page: ${nextData.chapters.size} chapters, hasMore=${nextData.hasMore}")
            if (nextData.chapters.isEmpty()) {
                ProComicDiag.logStage("CHAPTERS", page, "empty page terminates pagination")
                break
            }
            if (!seenPageFingerprints.add(fingerprint)) {
                ProComicDiag.logStage("CHAPTERS", page, "repeated page terminates pagination")
                break
            }
            all.addAll(nextData.chapters)
            hasMore = nextData.hasMore
            page++
            if (page > MAX_CHAPTER_PAGES) break
        }

        // Show all approved chapters regardless of language.
        // NOTE: procomic.net currently publishes EN chapters first, AR may follow.
        // Filtering to AR-only would result in 0 chapters for most series.
        val approved = all.filter { it.status == "approved" }
        ProComicDiag.logStage("CHAPTERS", 99,
            "total fetched=${all.size}, approved=${approved.size}, mangaUrl=$mangaUrl")

        val normalized = ProComicUtils.normalizeChapters(
            approved,
            diagTag = "CHAPTERS",
            diagUrl = url.toString(),
        )
        ProComicDiag.logStage(
            "CHAPTERS",
            99,
            "normalized=${normalized.size}, languages=${normalized.groupingBy { it.languageCode }.eachCount()}, " +
                "fallbacks=${normalized.count { it.isEnglishFallback }}",
        )
        val showPaidChapters = shouldShowPaidChapters()
        val visible = if (showPaidChapters) {
            normalized
        } else {
            normalized.filter { chapter ->
                ProComicUtils.classifyGateState(chapter.gate) !in PAID_GATE_STATES
            }
        }
        ProComicDiag.logStage(
            "CHAPTERS",
            99,
            "showPaidChapters=$showPaidChapters, visible=${visible.size}, hidden=${normalized.size - visible.size}",
        )
        return visible.map { it.toSChapter(mangaUrl) }
    }

    // ---- Page List ----
    // Canonical ProComic reader route:
    // https://procomic.pro/en/chapter/{slug}-{chapterNumber}-{chapterId}
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url.trim()
        val canonicalUrl = when {
            chapterUrl.startsWith("http") -> chapterUrl
            chapterUrl.startsWith("/en/chapter/") || chapterUrl.startsWith("/ar/chapter/") -> {
                "https://procomic.pro$chapterUrl"
            }
            else -> {
                // chapter.url format: /ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNumber}
                val parts = chapterUrl.trim('/').split('/')
                if (parts.size >= 7) {
                    val slug = parts[4]
                    val chapterId = parts[5]
                    val chapterNumber = parts[6]
                    "https://procomic.pro/en/chapter/$slug-$chapterNumber-$chapterId"
                } else if (parts.size >= 6) {
                    val slug = parts[3]
                    val chapterId = parts[4]
                    val chapterNumber = parts[5]
                    "https://procomic.pro/en/chapter/$slug-$chapterNumber-$chapterId"
                } else {
                    "https://procomic.pro$chapterUrl"
                }
            }
        }
        return GET(canonicalUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = readBoundedBody(response)
        val url = response.request.url.toString()
        ProComicDiag.logResponse("PAGES", response, body)

        val publicImages = ProComicUtils.extractPageImages(body, "PAGES", url)
        val pages = publicImages.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }.toMutableList()
        val protection = ProComicUtils.extractReaderProtection(body, "PAGES", url)
        // Current Reader RSC emits deferredMedia as a sibling of protectionV2. Keep the nested
        // fallback for older captures/variants while preferring the current sibling contract.
        val deferred = ProComicUtils.extractReaderDeferredMedia(body, "PAGES", url)
            ?: protection?.deferredMedia
        val deferredToken = deferred?.token
        val deferredSplitIndex = deferred?.splitIndex
        val chapterId = Regex("-(\\d+)$").find(response.request.url.pathSegments.lastOrNull().orEmpty())
            ?.groupValues?.getOrNull(1)?.toIntOrNull()

        if (deferredToken.isNullOrBlank() || deferredSplitIndex == null || chapterId == null) {
            ProComicDiag.logStage(
                "PAGES",
                99,
                "public images=${pages.size}, deferred media unavailable; protection=${protection?.version ?: 0}",
            )
            return pages
        }
        if (deferred.requireTurnstile || deferred.turnstileMode != null) {
            ProComicDiag.logStage(
                "PAGES",
                98,
                "deferred media requires server security verification; public images=${pages.size}",
            )
            return pages
        }

        val deferredData = fetchDeferredMedia(
            chapterId = chapterId,
            token = deferredToken,
            splitIndex = deferredSplitIndex,
            referer = url,
        )
        val directDeferred = deferredData.images
            .filter { ProComicUtils.isAllowedPageImageUrl(it) }
            .filterNot(publicImages::contains)
            .distinct()
        if (directDeferred.isNotEmpty()) {
            pages += directDeferred.mapIndexed { index, imageUrl ->
                Page(pages.size + index, imageUrl = imageUrl)
            }
        }

        val mapStartIndex = deferredData.splitIndex ?: deferred.splitIndex
        val protectedMaps = deferredData.maps.take(MAX_READER_DEFERRED_MAPS)
        if (protectedMaps.size != deferredData.maps.size) {
            ProComicDiag.logStage(
                "PAGES",
                97,
                "deferred map count exceeded bound=${MAX_READER_DEFERRED_MAPS}; truncated=${deferredData.maps.size - protectedMaps.size}",
            )
        }
        protectedMaps.forEachIndexed { index, mapToken ->
            pages += Page(
                pages.size,
                imageUrl = ProComicUtils.encodeProtectedPageUrl(
                    ProComicProtectedPagePayload(
                        chapterId = chapterId,
                        token = mapToken.token,
                        method = mapToken.method,
                        cdnPath = ProComicUtils.extractReaderCdnPath(body) ?: "cdn2",
                        pageIndex = mapStartIndex + index,
                    ),
                ),
            )
        }
        ProComicDiag.logStage(
            "PAGES",
            99,
            "public=${publicImages.size}, deferredImages=${directDeferred.size}, protectedMaps=${deferredData.maps.size}, total=${pages.size}",
        )
        return pages
    }

    private fun fetchDeferredMedia(
        chapterId: Int,
        token: String,
        splitIndex: Int,
        referer: String,
    ): ProComicDeferredMediaData {
        val request = GET(
            "$READER_BASE_URL/chapter-deferred-media/$chapterId?token=${URLEncoder.encode(token, "UTF-8")}&split=$splitIndex",
            headersBuilder()
                .set("Accept", "application/json")
                .set("Referer", referer)
                .build(),
        )
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("ProComic Reader: deferred media request failed (${response.code})")
            }
            ProComicUtils.json.decodeFromString<ProComicDeferredMediaResponse>(readBoundedBody(response)).data
                ?: throw Exception("ProComic Reader: deferred media response has no data")
        }
    }


    // ---- Image URL ----
    // Images are directly embedded in pageListParse, so this is unused.
    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")

    // ---- Image Request ----
    // Add Referer header for CDN image requests.
    override fun imageRequest(page: Page): Request {
        // headersBuilder() no longer contains RSC headers, so no removeAll() needed.
        val imageUrl = page.imageUrl ?: throw Exception("ProComic Reader: image URL is missing")
        val allowed = ProComicUtils.isAllowedPageImageUrl(imageUrl) ||
            ProComicUtils.isProtectedPageImageUrl(imageUrl)
        if (!allowed) {
            ProComicDiag.logStage("PAGES", 98, "rejected unrecognized image host")
            throw Exception("ProComic Reader: unrecognized image host")
        }
        val imageHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .set("Referer", "$baseUrl/")
            .build()
        return GET(imageUrl, imageHeaders)
    }

    // ---- Filters ----
    // The current Search endpoint honors type but ignores the tested genre parameter.
    // Do not advertise a filter that cannot change the server result set.
    override fun getFilterList() = FilterList(
        TypeFilter(),
    )

    // ---- DTO → Model conversion helpers ----

    private fun ProComicLatestSeries.toLatestSManga(): SManga = SManga.create().apply {
        url = "/ar/series/$type/$mangaId/$mangaSlug"
        title = this@toLatestSManga.mangaTitle
        thumbnail_url = this@toLatestSManga.coverImage?.takeIf { it.startsWith("http") }
        genre = this@toLatestSManga.type
            .takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
        status = mapPublicationStatus(
            progress = null,
            lifecycleStatus = this@toLatestSManga.status,
        )
    }

    private fun ProComicPopularContent.toPopularSManga(): SManga = SManga.create().apply {
        url = "/ar/series/$type/$id/$slug"
        title = this@toPopularSManga.title
        thumbnail_url = this@toPopularSManga.thumbnail?.takeIf { it.startsWith("http") }
            ?: this@toPopularSManga.coverImageApp?.desktop?.takeIf { it.startsWith("http") }
            ?: this@toPopularSManga.metadata?.coverImage?.takeIf { it.startsWith("http") }
            ?: this@toPopularSManga.thumbnail?.takeIf { it.startsWith("/") && !it.startsWith("//") }
                ?.let { path ->
                    this@toPopularSManga.cdnPath
                        ?.takeIf { it.matches(Regex("cdn\\d+")) }
                        ?.let { cdn -> "https://$cdn.procomic.net$path" }
                }
        description = this@toPopularSManga.metadata?.descriptions?.ar
            ?: this@toPopularSManga.metadata?.descriptions?.en
            ?: this@toPopularSManga.description
        genre = buildList {
            this@toPopularSManga.metadata?.genres?.forEach { add(it) }
            if (this@toPopularSManga.type.isNotBlank()) {
                add(this@toPopularSManga.type.replaceFirstChar { it.uppercase() })
            }
        }.distinct().joinToString(", ")
        author = listOfNotNull(
            this@toPopularSManga.metadata?.author,
            this@toPopularSManga.metadata?.artist,
        ).distinct().joinToString(", ").ifBlank { null }
        status = mapPublicationStatus(
            progress = this@toPopularSManga.progress,
            lifecycleStatus = this@toPopularSManga.status,
        )
    }

    private fun ProComicSeriesDto.toSManga(): SManga = SManga.create().apply {
        // URL pattern: /ar/series/{type}/{id}/{slug}
        url = "/ar/series/$type/$id/$slug"
        title = this@toSManga.title
        thumbnail_url = this@toSManga.coverImage?.takeIf { it.startsWith("http") }
            ?: this@toSManga.thumbnail?.takeIf { it.startsWith("http") }
            ?: this@toSManga.thumbnail?.takeIf { it.startsWith("/") && !it.startsWith("//") }
                ?.let { path ->
                    this@toSManga.cdnPath
                        ?.takeIf { it.matches(Regex("cdn\\d+")) }
                        ?.let { cdn -> "https://$cdn.procomic.net$path" }
                }
            ?: this@toSManga.coverImage

        // Use Arabic description if available, then English, then direct description field
        description = this@toSManga.metadata?.descriptions?.ar
            ?: this@toSManga.metadata?.descriptions?.en
            ?: this@toSManga.description

        // Build genre string: genres from metadata (plain strings) + comic type
        genre = buildList {
            this@toSManga.metadata?.genres?.forEach { add(it) }
            if (this@toSManga.type.isNotBlank() &&
                this@toSManga.type.replaceFirstChar { it.uppercase() } !in this) {
                add(this@toSManga.type.replaceFirstChar { it.uppercase() })
            }
        }.joinToString(", ")

        // Author/artist from metadata
        author = listOfNotNull(
            this@toSManga.metadata?.author,
            this@toSManga.metadata?.artist,
        ).distinct().joinToString(", ").ifBlank { null }

        // The top-level progress field carries publication lifecycle; top-level status is
        // approval/access metadata and is used only as a fallback for known lifecycle values.
        status = mapPublicationStatus(
            progress = this@toSManga.progress,
            lifecycleStatus = this@toSManga.status,
        )
    }

    private fun ProComicNormalizedChapter.toSChapter(mangaUrl: String): SChapter = SChapter.create().apply {
        val chapter = source
        // Full reader URL: /ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNumber}
        url = "$mangaUrl/${chapter.id}/${chapter.chapterNumber}"
        name = buildString {
            append("الفصل ${chapter.chapterNumber}")  // "Chapter N" in Arabic
            if (languageCode != "AR") {
                append(" [$languageDisplay]")
            }
            if (!chapter.title.isNullOrBlank()) {
                append(" - ")
                append(chapter.title)
            }
        }
        chapter_number = numericNumber ?: -1f
        scanlator = chapter.translator?.takeIf { it.isNotBlank() } ?: "Pro Chan"
    }

}
