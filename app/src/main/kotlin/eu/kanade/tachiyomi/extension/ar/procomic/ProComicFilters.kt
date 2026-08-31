package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.source.model.Filter

/**
 * Filters supported by the current ProComic Search contract.
 *
 * The live endpoint honors `type=manga|manhwa|manhua|novel`. The tested `genre` parameter is
 * currently ignored by the server, so GenreFilter is intentionally not exposed to Mihon until a
 * current official route proves that it changes the result set.
 */
class TypeFilter : Filter.Select<String>(
    "النوع (Type)",
    arrayOf("الكل (All)", "مانغا (Manga)", "مانهوا (Manhwa)", "مانها (Manhua)"),
    0,
) {
    val typeValues = listOf("", "manga", "manhwa", "manhua")
}
