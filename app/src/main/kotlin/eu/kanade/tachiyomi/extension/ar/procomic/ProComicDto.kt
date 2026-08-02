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
data class ProComicSeriesListResponse(
    val initialSeries: List<ProComicSeriesDto> = emptyList(),
    val total: Int = 0,
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
    @SerialName("coverImageApp") val coverImageApp: String? = null,
    val metadata: ProComicSeriesMetadata? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ProComicSeriesMetadata(
    val originalTitle: String? = null,
    val altTitles: List<String>? = null,
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
 * Chapter DTO — from chapter list embedded in series detail RSC.
 * Language values observed: "AR", "EN", "ZH"
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
