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
import okhttp3.Request
import okhttp3.Response
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
 *   - Chapter pages are limited to publicImageCount (currently 3) for guest users.
 *     This is enforced server-side by cdn2.procomic.pro (nginx 403 for any additional pages).
 *     A login flow would be required to access full chapters — out of scope for this version.
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
    }

    private fun readBoundedBody(response: Response): String {
        val body = response.body ?: throw Exception("ProComic: response body is missing")
        val declaredLength = body.contentLength()
        if (declaredLength > MAX_RESPONSE_BYTES) {
            throw Exception("ProComic: response exceeds ${MAX_RESPONSE_BYTES} bytes")
        }
        val bytes = body.source().readByteArray(MAX_RESPONSE_BYTES.toLong() + 1L)
        if (bytes.size > MAX_RESPONSE_BYTES) {
            throw Exception("ProComic: response exceeds ${MAX_RESPONSE_BYTES} bytes")
        }
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun chapterPageFingerprint(data: ProComicChapterListResponse): String =
        data.chapters.joinToString(",") { it.id.toString() }

    // Initialized when Mihon builds the source preference screen. Until then, preserve the
    // historical behavior of showing every normalized chapter.
    private var sourcePreferences: SharedPreferences? = null

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
    // GET /api/public/series/search?status=approved&limit=18&page={page}&sort=latest&search={query}
    //
    // Type filter is supported server-side via &type={manga|manhwa|manhua}.
    //
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = buildString {
            append(baseUrl)
            append("/api/public/series/search?status=approved&limit=18&page=$page&sort=latest")

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

        val searchResponse = ProComicUtils.json.decodeFromString<ProComicSearchResponse>(body)
        val nonNovel = searchResponse.data.filter { it.type != "novel" }
        val mangas = nonNovel.map { it.toSManga() }

        val currentPage = searchResponse.meta?.page ?: 1
        val totalPages = searchResponse.meta?.pages ?: 1
        val hasNextPage = currentPage < totalPages

        ProComicDiag.logStage("SEARCH", 99,
            "MangasPage: ${mangas.size} items (filtered from ${searchResponse.data.size}), hasNextPage=$hasNextPage (page $currentPage of $totalPages)")

        return MangasPage(mangas, hasNextPage = hasNextPage)
    }

    // ---- Series Detail ----
    // manga.url remains "/ar/series/{type}/{id}/{slug}" for chapter REST parsing.
    // The live canonical Details route is "/ar/series/{slug}-{id}".
    override fun mangaDetailsRequest(manga: SManga): Request {
        val detailsPath = canonicalDetailsPath(manga.url)
        return GET("$baseUrl$detailsPath?_rsc=det", rscHeaders())
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
        return when (val result = ProComicUtils.extractSeriesDetail(
            body,
            diagTag = "DETAIL",
            diagUrl = url,
            expectedId = expectedId,
            expectedSlug = expectedSlug,
        )) {
            is ProComicDetailsResult.Complete -> result.series.toSManga()
            is ProComicDetailsResult.Restricted -> result.details.toSManga()
            null -> throw Exception("ProComic: could not parse series details from RSC response")
        }
    }

    private fun ProComicRestrictedDetails.toSManga(): SManga = SManga.create().apply {
        url = "/ar/series/$type/$id/$slug"
        title = this@toSManga.title
        thumbnail_url = coverImage?.takeIf { it.startsWith("http") }
        description = description?.takeIf { it.isNotBlank() }
        genre = type.replaceFirstChar { it.uppercase() }
        status = SManga.UNKNOWN
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
            val next = client.newCall(
                GET("$baseUrl/api/chapters?contentId=$contentId&page=$page", headers)
            ).execute()
            val nextData = ProComicUtils.json.decodeFromString<ProComicChapterListResponse>(
                readBoundedBody(next)
            )
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
        val body = response.body!!.string()
        val url = response.request.url.toString()
        ProComicDiag.logResponse("PAGES", response, body)

        val images = ProComicUtils.extractPageImages(body, "PAGES", url)
        ProComicDiag.logStage("PAGES", 99, "images found: ${images.size}")

        return images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
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
        if (!ProComicUtils.isAllowedPageImageUrl(imageUrl)) {
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
    override fun getFilterList() = FilterList(
        TypeFilter(),
        GenreFilter(),
    )

    // ---- DTO → Model conversion helpers ----

    private fun ProComicLatestSeries.toLatestSManga(): SManga = SManga.create().apply {
        url = "/ar/series/$type/$mangaId/$mangaSlug"
        title = this@toLatestSManga.mangaTitle
        thumbnail_url = this@toLatestSManga.coverImage?.takeIf { it.startsWith("http") }
        genre = this@toLatestSManga.type
            .takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
        status = when (this@toLatestSManga.status?.lowercase()) {
            "ongoing", "مستمر" -> SManga.ONGOING
            "completed", "مكتمل" -> SManga.COMPLETED
            "dropped", "متوقف" -> SManga.CANCELLED
            "hiatus", "متوقف مؤقتا", "متوقف مؤقتًا" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun ProComicPopularContent.toPopularSManga(): SManga = SManga.create().apply {
        url = "/ar/series/$type/$id/$slug"
        title = this@toPopularSManga.title
        thumbnail_url = this@toPopularSManga.thumbnail?.takeIf { it.startsWith("http") }
            ?: this@toPopularSManga.thumbnail?.let { "$baseUrl$it" }
            ?: this@toPopularSManga.metadata?.coverImage?.takeIf { it.startsWith("http") }
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
        status = when (this@toPopularSManga.metadata?.viewStatus?.lowercase()) {
            "exclusive" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "dropped" -> SManga.CANCELLED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun ProComicSeriesDto.toSManga(): SManga = SManga.create().apply {
        // URL pattern: /ar/series/{type}/{id}/{slug}
        url = "/ar/series/$type/$id/$slug"
        title = this@toSManga.title
        thumbnail_url = this@toSManga.coverImage?.takeIf { it.startsWith("http") }
            ?: this@toSManga.thumbnail?.takeIf { it.startsWith("http") }
            ?: this@toSManga.thumbnail?.let { "https://app.procomic.net$it" }
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

        // Status: the site's 'status' field is 'approved' for all active series.
        // Real ongoing/completed status is in metadata.viewStatus ('exclusive', 'free', etc.)
        // Map based on viewStatus until a confirmed mapping is available.
        status = when (this@toSManga.metadata?.viewStatus?.lowercase()) {
            "exclusive" -> SManga.ONGOING   // Exclusive = actively being published
            "completed" -> SManga.COMPLETED
            "dropped" -> SManga.CANCELLED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
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
