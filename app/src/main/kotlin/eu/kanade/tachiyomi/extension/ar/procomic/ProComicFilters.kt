package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.model.Filter

/**
 * Filters for the ProComic extension.
 *
 * Type filter: Comic type (manga, manhwa, manhua).
 * Genre filter: Genre selection from the platform's genre list.
 *
 * Confirmed from Stage 5 live testing (2026-07-27/28):
 *   - genre parameter accepts genre name strings (e.g. &genre=Action)
 *   - genre strings are case-insensitive
 *   - type parameter accepts: manga, manhwa, manhua
 *   - sort parameter: "popular", "latest" (used in popular/latest endpoints)
 *   - All filters work server-side — they reduce the initialSeries count.
 */

class TypeFilter : Filter.Select<String>(
    "النوع (Type)",
    arrayOf("الكل (All)", "مانغا (Manga)", "مانهوا (Manhwa)", "مانها (Manhua)"),
    0
) {
    val typeValues = listOf("", "manga", "manhwa", "manhua")
}

class GenreFilter : Filter.Select<String>(
    "التصنيف (Genre)",
    GENRES.map { it.first }.toTypedArray(),
    0
) {
    val genreValues = GENRES.map { it.second }
}

/**
 * Genre display name → genre query parameter value.
 * Values confirmed via Stage 5 live testing: server accepts genre name strings.
 * Genres sourced from the 'metadata.genres' field of the live initialSeries RSC response.
 */
val GENRES = listOf(
    Pair("الكل (All)", ""),
    Pair("أكشن (Action)", "Action"),
    Pair("مغامرات (Adventure)", "Adventure"),
    Pair("كوميديا (Comedy)", "Comedy"),
    Pair("دراما (Drama)", "Drama"),
    Pair("خيال (Fantasy)", "Fantasy"),
    Pair("رعب (Horror)", "Horror"),
    Pair("فنون قتالية (Martial Arts)", "Martial Arts"),
    Pair("غموض (Mystery)", "Mystery"),
    Pair("رومانسية (Romance)", "Romance"),
    Pair("خيال علمي (Sci-Fi)", "Sci-Fi"),
    Pair("مدرسي (School Life)", "School Life"),
    Pair("شونن (Shounen)", "Shounen"),
    Pair("حياة يومية (Slice of Life)", "Slice of Life"),
    Pair("خارق للطبيعة (Supernatural)", "Supernatural"),
    Pair("نظام (System)", "System"),
    Pair("إيساكاي (Isekai)", "Isekai"),
)
