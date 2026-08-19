package eu.kanade.tachiyomi.extension.ar.procomic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

/**
 * Reconstructs one protected ProComic Reader page from the site's deferred map contract.
 *
 * The Page URL carries only a short-lived map capability. At image-request time this interceptor
 * asks the documented proxy-plan endpoint for a fresh map, downloads its signed AVIF pieces, and
 * replaces the virtual response with one normal JPEG image for Mihon's reader.
 */
class ProComicImageInterceptor(
    private val tileClient: OkHttpClient,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment ?: return chain.proceed(request)
        val payload = ProComicUtils.decodeProtectedPagePayload(fragment)
            ?: return chain.proceed(request)
        val map = fetchMap(request, payload)
        return reconstruct(request, map)
    }

    private fun fetchMap(
        pageRequest: Request,
        payload: ProComicProtectedPagePayload,
    ): ProComicProtectedMap {
        val body = ProComicUtils.json.encodeToString(
            ProComicMapProxyRequest(
                token = payload.token,
                method = payload.method,
                cdnPath = payload.cdnPath,
                pageIndex = payload.pageIndex,
            ),
        ).toRequestBody(JSON_MEDIA_TYPE)
        val request = pageRequest.newBuilder()
            .url("$READER_BASE_URL/chapter-map-proxy-plan/${payload.chapterId}")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        return tileClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("ProComic Reader: protected map request failed (${response.code})")
            }
            ProComicUtils.json.decodeFromString<ProComicMapProxyResponse>(
                response.body?.string() ?: throw IOException("ProComic Reader: protected map body is missing"),
            ).data?.map ?: throw IOException("ProComic Reader: protected map response has no map")
        }
    }

    private fun reconstruct(
        pageRequest: Request,
        map: ProComicProtectedMap,
    ): Response {
        val width = map.dim.getOrNull(0)?.coerceAtLeast(1)
            ?: throw IOException("ProComic Reader: protected map width is missing")
        val height = map.dim.getOrNull(1)?.coerceAtLeast(1)
            ?: throw IOException("ProComic Reader: protected map height is missing")
        if (width.toLong() * height.toLong() > MAX_COMPOSITE_PIXELS) {
            throw IOException("ProComic Reader: protected page is too large")
        }

        val order = if (map.order.isNotEmpty()) map.order else map.pieces.indices.toList()
        if (order.isEmpty() || order.size > MAX_TILES || order.any { it !in map.pieces.indices }) {
            throw IOException("ProComic Reader: protected map geometry is invalid")
        }
        val rects = if (map.rects.size == order.size) {
            map.rects
        } else {
            fallbackRectangles(map.mode, width, height, order.size)
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        var responseBuilder: Response.Builder? = null
        try {
            order.forEachIndexed { outputIndex, sourceIndex ->
                val pieceUrl = map.pieces.getOrNull(sourceIndex)
                    ?.takeIf { ProComicUtils.isAllowedProtectedTileUrl(it) }
                    ?: throw IOException("ProComic Reader: protected tile URL is invalid")
                val rect = rects[outputIndex]
                val destination = Rect(
                    rect.left.coerceIn(0, width - 1),
                    rect.top.coerceIn(0, height - 1),
                    (rect.left + rect.width).coerceIn(1, width),
                    (rect.top + rect.height).coerceIn(1, height),
                )
                if (destination.right <= destination.left || destination.bottom <= destination.top) {
                    throw IOException("ProComic Reader: protected tile rectangle is invalid")
                }

                val tileRequest = pageRequest.newBuilder()
                    .url(pieceUrl)
                    .header("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                    .build()
                tileClient.newCall(tileRequest).execute().use { tileResponse ->
                    if (!tileResponse.isSuccessful) {
                        throw IOException("ProComic Reader: protected tile request failed (${tileResponse.code})")
                    }
                    if (responseBuilder == null) {
                        responseBuilder = tileResponse.newBuilder().request(pageRequest)
                    }
                    val bytes = tileResponse.body?.bytes()
                        ?: throw IOException("ProComic Reader: protected tile body is missing")
                    val tile = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: runCatching { avifCoder.decode(bytes) }.getOrNull()
                        ?: throw IOException("ProComic Reader: protected tile could not be decoded")
                    try {
                        canvas.drawBitmap(tile, null, destination, paint)
                    } finally {
                        tile.recycle()
                    }
                }
            }

            val buffer = Buffer()
            if (!result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, buffer.outputStream())) {
                throw IOException("ProComic Reader: reconstructed page could not be encoded")
            }
            val responseBody = buffer.readByteArray().toResponseBody(JPEG_MEDIA_TYPE)
            return (responseBuilder ?: throw IOException("ProComic Reader: protected page had no tile response"))
                .header("Content-Type", JPEG_MEDIA_TYPE.toString())
                .body(responseBody)
                .build()
        } finally {
            result.recycle()
        }
    }

    private fun fallbackRectangles(
        mode: String,
        width: Int,
        height: Int,
        count: Int,
    ): List<ProComicMapRect> {
        val parts = mode.split("_", limit = 2)
        val grid = parts.getOrNull(0) == "grid"
        val dimensions = parts.getOrNull(1)?.split("x")
        val columns = if (grid) dimensions?.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(1) ?: count else count
        val rows = if (grid) dimensions?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1 else 1
        val actualColumns = columns.coerceAtMost(count)
        return List(count) { index ->
            val column = index % actualColumns
            val row = if (grid) index / actualColumns else 0
            val left = (width * column) / actualColumns
            val right = (width * (column + 1)) / actualColumns
            val top = if (grid) (height * row) / rows else 0
            val bottom = if (grid) (height * (row + 1)) / rows else height
            ProComicMapRect(left, top, right - left, bottom - top)
        }
    }

    private companion object {
        const val READER_BASE_URL = "https://procomic.pro"
        const val MAX_TILES = 32
        const val MAX_COMPOSITE_PIXELS = 40_000_000L
        const val JPEG_QUALITY = 95
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
        val avifCoder = HeifCoder()
    }
}
