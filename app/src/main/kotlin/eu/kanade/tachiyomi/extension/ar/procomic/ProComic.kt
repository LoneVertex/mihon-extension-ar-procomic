package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
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
 * Architecture: HttpSource with RSC (React Server Components) stream parsing.
 *   All data is fetched via RSC requests (RSC: 1 header + ?_rsc= query param),
 *   which return the text/x-component wire format containing embedded JSON data.
 *   No HTML scraping, no REST API (no public /api/ routes exist).
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
class ProComic : HttpSource() {

    override val name = "ProComic"
    override val baseUrl = "https://procomic.net"
    override val lang = "ar"
    override val supportsLatest = true

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
    // CONFIRMED 2026-08-02: the server ignores sort= and page= — it always returns all
    // series (currently 18 total, 14 non-novel) regardless of query params.
    // sort=popular is kept for forward-compatibility in case the server adds support.
    // Client-side sort: Popular = most recently ADDED (id descending).
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/ar/series?_rsc=pop$page", rscHeaders())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body!!.string()
        val url = response.request.url.toString()
        ProComicDiag.logResponse("POPULAR", response, body)
        val series = ProComicUtils.extractSeriesList(body, "POPULAR", url)
        // Sort newest series (highest id) first as a proxy for "popular" on a new site
        val sorted = series.sortedByDescending { it.id }
        val mangas = sorted.map { it.toSManga() }
        // Server returns all series in a single page — never request page 2
        ProComicDiag.logStage("POPULAR", 99,
            "MangasPage: ${mangas.size} items, hasNextPage=false (server ignores page param)")
        return MangasPage(mangas, hasNextPage = false)
    }

    // ---- Latest Updates ----
    // Client-side sort: Latest = most recently UPDATED (updatedAt descending).
    // This gives visual differentiation from Popular despite the same server payload.
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/ar/series?_rsc=lat$page", rscHeaders())
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val body = response.body!!.string()
        val url = response.request.url.toString()
        ProComicDiag.logResponse("LATEST", response, body)
        val series = ProComicUtils.extractSeriesList(body, "LATEST", url)
        // Sort most recently updated series first (ISO-8601 strings sort lexicographically)
        val sorted = series.sortedByDescending { it.updatedAt ?: "" }
        val mangas = sorted.map { it.toSManga() }
        ProComicDiag.logStage("LATEST", 99,
            "MangasPage: ${mangas.size} items, hasNextPage=false")
        return MangasPage(mangas, hasNextPage = false)
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
        val body = response.body!!.string()
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
        val body = response.body!!.string()
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
            response.body!!.string()
        )
        ProComicDiag.logStage("CHAPTERS", 1,
            "REST API: total=${firstPage.total}, page1=${firstPage.chapters.size}, " +
            "hasMore=${firstPage.hasMore}")

        val all = firstPage.chapters.toMutableList()

        // Fetch remaining pages while hasMore == true
        var page = 2
        var hasMore = firstPage.hasMore
        while (hasMore) {
            val next = client.newCall(
                GET("$baseUrl/api/chapters?contentId=$contentId&page=$page", headers)
            ).execute()
            val nextData = ProComicUtils.json.decodeFromString<ProComicChapterListResponse>(
                next.body!!.string()
            )
            ProComicDiag.logStage("CHAPTERS", page,
                "page=$page: ${nextData.chapters.size} chapters, hasMore=${nextData.hasMore}")
            all.addAll(nextData.chapters)
            hasMore = nextData.hasMore
            page++
            if (page > 50) break // safety guard against infinite loop
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
        return normalized.map { it.toSChapter(mangaUrl) }
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
        val imageHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    // ---- Filters ----
    override fun getFilterList() = FilterList(
        TypeFilter(),
        GenreFilter(),
    )

    // ---- DTO → Model conversion helpers ----

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
