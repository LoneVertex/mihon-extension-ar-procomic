package eu.kanade.tachiyomi.extension.ar.procomic

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Deserializes a JSON field that can be either:
 *   - a JSON string:  "some name"
 *   - a JSON array:   ["name1", "name2"]
 *   - absent / null
 *
 * Arrays are joined with ", ". Used for `author` and `artist` fields which
 * the procomic.pro API occasionally returns as arrays when multiple credits exist.
 *
 * ROOT CAUSE FIX 2026-08-02: JsonDecodingException at $[10].metadata.author
 * when server sends ["村里无敌帅","番茄小说"] instead of a single string.
 */
private object StringOrListSerializer : KSerializer<String?> {
    private val delegate = String.serializer().nullable

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> element
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
                .joinToString(", ")
                .ifEmpty { null }
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        delegate.serialize(encoder, value)
    }
}

/**
 * DTOs for the ProComic extension, mapped from the RSC wire-format initialSeries payload.
 *
 * Actual data structure confirmed from Stage 3C+5 recon (2026-07-26/27).
 * Series objects are embedded as props in: "$Le",null,{"initialSeries":[...]}
 *
 * Confirmed field order in RSC: id, title, slug, description, type, status,
 *   thumbnail, coverImage, coverImageApp, is_sensitive_image, metadata, created_at, updated_at
 */

@Serializable
data class ProComicAppImage(
    val mobile: String? = null,
    val desktop: String? = null,
)

@Serializable
data class ProComicSearchResponse(
    val data: List<ProComicSeriesDto> = emptyList(),
    val meta: ProComicSearchMeta? = null,
)

@Serializable
data class ProComicSearchMeta(
    val total: Int? = null,
    val page: Int = 1,
    val limit: Int = 18,
    val pages: Int = 1,
)

@Serializable
data class ProComicSeriesListResponse(
    val initialSeries: List<ProComicSeriesDto> = emptyList(),
    val total: Int = 0,
)

/**
 * Public Popular feed: `/api/public/content/popular-new?limit=N`.
 * Unlike Search/Details, each row wraps the series in `content` and exposes `viewCount`
 * as a string. The response has no observed continuation metadata.
 */
@Serializable
data class ProComicPopularResponse(
    val success: Boolean = false,
    val data: List<ProComicPopularItem> = emptyList(),
)

@Serializable
data class ProComicPopularItem(
    val content: ProComicPopularContent,
    val viewCount: String? = null,
)

@Serializable
data class ProComicPopularContent(
    val id: Int,
    val title: String,
    val slug: String,
    val description: String? = null,
    val type: String,
    val status: String? = null,
    val thumbnail: String? = null,
    val metadata: ProComicSeriesMetadata? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * Public Latest feed: `/api/public/content/latest-updates?limit=18&category=all&page=N`.
 * This schema is intentionally separate from Popular: series fields are flat and the
 * latest chapter summaries carry their own language, timestamp, and gate metadata.
 */
@Serializable
data class ProComicLatestResponse(
    val success: Boolean = false,
    val data: List<ProComicLatestSeries> = emptyList(),
)

@Serializable
data class ProComicLatestSeries(
    val mangaId: Int,
    val mangaSlug: String,
    val mangaTitle: String,
    val coverImage: String? = null,
    @SerialName("cdn_path") val cdnPath: String? = null,
    val type: String,
    val origin: String? = null,
    val status: String? = null,
    val isBlockedSeries: Boolean = false,
    val isSensitiveImage: Boolean = false,
    val viewStatus: String? = null,
    val supportStatus: String? = null,
    val chapters: List<ProComicLatestChapterSummary> = emptyList(),
)

@Serializable
data class ProComicLatestChapterSummary(
    val id: Int,
    val slug: String? = null,
    val number: String,
    val language: String,
    val publishedAt: String? = null,
    val supportMode: String? = null,
    val coinsRequired: Int? = null,
    val hasShortlink: Boolean? = null,
    val lockedForever: Boolean? = null,
    val lockedByCoins: Boolean? = null,
    val lockedByExclusive: Boolean? = null,
)

@Serializable
data class ProComicSeriesDto(
    val id: Int,
    val title: String,
    val slug: String,
    val description: String? = null,
    val type: String,
    val status: String? = null,
    val thumbnail: String? = null,
    @SerialName("coverImage") val coverImage: String? = null,
    val metadata: ProComicSeriesMetadata? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

sealed interface ProComicDetailsResult {
    data class Complete(val series: ProComicSeriesDto) : ProComicDetailsResult
    data class Restricted(val details: ProComicRestrictedDetails) : ProComicDetailsResult
}

@Serializable
data class ProComicRestrictedDetails(
    val id: Int,
    val title: String,
    val type: String,
    val slug: String,
    val restricted: Boolean,
    val coverImage: String? = null,
    val description: String? = null,
    val totalChapters: Int? = null,
    val latestChapterNumber: String? = null,
    val latestChapterDate: String? = null,
    val readHref: String? = null,
    val readIsExternal: Boolean? = null,
    val originalSources: List<String> = emptyList(),
)

@Serializable
data class ProComicRestrictedParams(
    val type: String,
    val id: String,
    val slug: String,
)

data class ProComicRestrictedSummary(
    val coverImage: String? = null,
    val description: String? = null,
    val totalChapters: Int? = null,
    val latestChapterNumber: String? = null,
    val latestChapterDate: String? = null,
    val readHref: String? = null,
    val readIsExternal: Boolean? = null,
    val originalSources: List<String> = emptyList(),
)

@Serializable
data class ProComicSeriesMetadata(
    val originalTitle: String? = null,
    val altTitles: List<String>? = null,
    val coverImage: String? = null,
    @Serializable(with = StringOrListSerializer::class)
    val author: String? = null,
    @Serializable(with = StringOrListSerializer::class)
    val artist: String? = null,
    val year: String? = null,
    val origin: String? = null,
    val viewStatus: String? = null,           // "exclusive", "free", etc.
    val genres: List<String>? = null,         // Genre names as plain strings
    @SerialName("descriptions") val descriptions: ProComicDescriptions? = null,
    val exclusiveLockStrategy: String? = null,
    val exclusiveUniversalConfigs: Map<String, ProComicExclusiveConfig>? = null,
    val exclusivePrice: Map<String, Int>? = null,
)

@Serializable
data class ProComicDescriptions(
    val ar: String? = null,
    val en: String? = null,
    val zh: String? = null,
)

@Serializable
data class ProComicExclusiveConfig(
    val lockCount: Int = 0,
    val timeLockHours: Int = 0,
    val freeChapters: Int = 0,
)

/**
 * Chapter DTO — used by BOTH:
 *   1. RSC parsing (key "initialChapters" for new-style series >= id ~686)
 *   2. REST API response: GET /api/chapters?contentId={id}
 *
 * Language values observed: "AR", "EN", "ZH"
 * NOTE: many series only have "EN" chapters — the AR filter was removed.
 */
@Serializable
data class ProComicChapterDto(
    val id: Int,
    @SerialName("content_id") val contentId: Int = 0,
    @SerialName("chapter_number") val chapterNumber: String,
    val title: String? = null,
    val language: String,
    val translator: String? = null,
    @SerialName("uploader_id") val uploaderId: Int? = null,
    val status: String? = null,
    @SerialName("cdn_path") val cdnPath: String? = null,
    val metadata: ProComicChapterMetadata? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val supportMode: String? = null,
    @SerialName("coins_unlocks") val coinsUnlocks: Int = 0,
    @SerialName("shortlink_unlocks") val shortlinkUnlocks: Int = 0,
    val coinsRequired: Int? = null,
    val hasShortlink: Boolean? = null,
    val lockedForever: Boolean? = null,
    val lockedByCoins: Boolean? = null,
    val lockedByExclusive: Boolean? = null,
)

/**
 * Response wrapper for REST API: GET /api/chapters?contentId={seriesId}[&page=N]
 *
 * Confirmed structure (2026-08-03):
 *   {"chapters": [...ProComicChapterDto...], "total": 34, "hasMore": true}
 * Page size appears to be 20. Pagination required for series with > 20 chapters.
 */
@Serializable
data class ProComicChapterListResponse(
    val chapters: List<ProComicChapterDto> = emptyList(),
    val total: Int? = null,      // server currently returns null, not an int
    val hasMore: Boolean = false,
)

data class ProComicChapterGate(
    val supportMode: String?,
    val coinsUnlocks: Int,
    val shortlinkUnlocks: Int,
    val coinsRequired: Int?,
    val hasShortlink: Boolean?,
    val lockedForever: Boolean?,
    val lockedByCoins: Boolean?,
    val lockedByExclusive: Boolean?,
    val publicImageCount: Int?,
)

/**
 * Conservative access classification for a chapter.
 *
 * [RESTRICTED_AUTH_REQUIRED] is a content-access state, not a paid state. It must never be
 * inferred from a denied/restricted response and must remain visible when paid chapters are hidden.
 */
enum class ProComicGateState {
    FREE,
    COIN_LOCKED,
    EXCLUSIVE,
    SHORTLINK_UNLOCK,
    PERMANENTLY_LOCKED,
    RESTRICTED_AUTH_REQUIRED,
    UNKNOWN,
}

data class ProComicNormalizedChapter(
    val source: ProComicChapterDto,
    val languageCode: String,
    val languageDisplay: String,
    val numericNumber: Float?,
    val gate: ProComicChapterGate,
    val isEnglishFallback: Boolean = false,
)

@Serializable
data class ProComicChapterMetadata(
    @SerialName("protectionV2") val protection: ProComicProtection? = null,
)

@Serializable
data class ProComicProtection(
    @SerialName("publicImageCount") val publicImageCount: Int,
    val version: Int = 0,
)
