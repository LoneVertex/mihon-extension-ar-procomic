package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response

/**
 * ProComic Tachiyomi/Mihon Extension
 *
 * Source: procomic.pro (Arabic manga/manhwa aggregator)
 * Platform: Custom Next.js App Router (RSC streaming), internal brand "ProChan"
 * Previously known as: prochan.net → procomic.net → procomic.pro
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
    override val baseUrl = "https://procomic.pro"
    override val lang = "ar"
    override val supportsLatest = true

    // Mobile Chrome UA is required — plain curl UA gets same response, but this
    // matches what a real Tachiyomi+WebView session would present.
    private val mobileUserAgent =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * Headers required for RSC requests:
     *   - User-Agent: Mobile Chrome (confirmed working with WAF in Stage 3C)
     *   - RSC: 1 — triggers Next.js RSC streaming response (text/x-component)
     *   - Accept-Language: ar,en;q=0.9 — returns Arabic-first content
     *   - Next-Router-State-Tree: URL-encoded empty router tree for compatibility
     */
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", mobileUserAgent)
        .add("RSC", "1")
        .add("Accept-Language", "ar,en;q=0.9")
        .add("Next-Router-State-Tree", "%5B%22%22%2C%7B%7D%5D")
        .add("Referer", baseUrl)

    // ---- Popular ----
    // Confirmed: /ar/series?_rsc=1 returns 165KB RSC with series listing
    // Sort parameter "popular" is inferred — verify with live test in Task 5
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/ar/series?sort=popular&page=$page&_rsc=pop$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body!!.string()
        val series = ProComicUtils.extractSeriesList(body)
        val mangas = series.map { it.toSManga() }
        // Heuristic: if we got a full page of results, assume there's a next page
        return MangasPage(mangas, hasNextPage = mangas.size >= 20)
    }

    // ---- Latest Updates ----
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/ar/series?sort=latest&page=$page&_rsc=lat$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // ---- Search ----
    //
    // EVIDENCE: Stage 5 verification confirmed that procomic.pro search is fully client-side.
    // RSC requests with ?search=hunter always return 0 bytes regardless of Next-Router-State-Tree.
    // The Next.js App Router does NOT server-render search results for this route.
    //
    // APPROACH: Fetch the full series RSC listing (same as popular/latest), then filter
    // client-side by title substring match. This is a valid fallback for sites without
    // server-side search. Filter params (type, genre) are passed as URL params since
    // the server may respect them for the listing even if text search is client-only.
    //
    // We store the query per thread for use in searchMangaParse.
    private val pendingSearchQuery = ThreadLocal<String>()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        pendingSearchQuery.set(query.trim())
        val url = buildString {
            append(baseUrl)
            append("/ar/series?page=$page")

            filters.forEach { filter ->
                when (filter) {
                    is TypeFilter -> {
                        if (filter.state > 0) {
                            append("&type=")
                            append(filter.typeValues[filter.state])
                        }
                    }
                    is GenreFilter -> {
                        if (filter.state > 0) {
                            append("&genre=")
                            append(filter.genreValues[filter.state])
                        }
                    }
                    else -> {}
                }
            }

            // RSC marker
            append("&_rsc=src$page")
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body!!.string()
        val series = ProComicUtils.extractSeriesList(body)
        val query = pendingSearchQuery.get() ?: ""
        val filtered = if (query.isBlank()) {
            series
        } else {
            series.filter { dto ->
                dto.title.contains(query, ignoreCase = true) ||
                dto.slug.replace("-", " ").contains(query, ignoreCase = true)
            }
        }
        val mangas = filtered.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = mangas.size >= 20 && query.isBlank())
    }

    // ---- Series Detail ----
    // manga.url is stored as "/ar/series/{type}/{id}/{slug}" (see toSManga)
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}?_rsc=det", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body!!.string()
        return ProComicUtils.extractSeriesDetail(body)?.toSManga()
            ?: throw Exception("ProComic: could not parse series details from RSC response")
    }

    // ---- Chapter List ----
    // Chapter list is embedded in the same RSC response as series detail.
    // We reuse the mangaDetailsRequest endpoint.
    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body!!.string()
        // Reconstruct the manga URL from the request URL (remove ?_rsc=det)
        val mangaUrl = response.request.url.toString()
            .removePrefix(baseUrl)
            .substringBefore("?")

        return ProComicUtils.extractChapterList(body).map { dto ->
            dto.toSChapter(mangaUrl)
        }
    }

    // ---- Page List ----
    // chapter.url stores the full reader path:
    //   /ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNumber}
    // CDN enforces publicImageCount (3) server-side for guests.
    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl${chapter.url}?_rsc=pgs", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body!!.string()
        val images = ProComicUtils.extractPageImages(body)

        if (images.isEmpty()) {
            // Escalation: no images found in RSC — CDN may require auth.
            // Return empty list; Tachiyomi will show empty chapter.
            // Follow-up: implement WebView session cookie propagation.
            return emptyList()
        }

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
        val imageHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .set("Referer", "$baseUrl/")
            .removeAll("RSC")
            .removeAll("Next-Router-State-Tree")
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
        thumbnail_url = this@toSManga.thumbnail ?: this@toSManga.coverImage

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

    private fun ProComicChapterDto.toSChapter(mangaUrl: String): SChapter = SChapter.create().apply {
        // Full reader URL: /ar/series/{type}/{seriesId}/{slug}/{chapterId}/{chapterNumber}
        url = "$mangaUrl/$id/$chapterNumber"
        name = buildString {
            append("الفصل $chapterNumber")  // "Chapter N" in Arabic
            if (!this@toSChapter.title.isNullOrBlank()) {
                append(" - ")
                append(this@toSChapter.title)
            }
        }
        chapter_number = chapterNumber.toFloatOrNull() ?: -1f
        scanlator = translator?.takeIf { it.isNotBlank() } ?: "Pro Chan"
    }

}
