package eu.kanade.tachiyomi.extension.ar.procomic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import org.aomedia.avif.android.AvifDecoder
import eu.kanade.tachiyomi.network.GET
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

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
        if (!ProComicUtils.isProtectedPageImageUrl(request.url.toString())) {
            return chain.proceed(request)
        }
        val fragment = request.url.fragment ?: return chain.proceed(request)
        val payload = ProComicUtils.decodeProtectedPagePayload(fragment)
            ?: return chain.proceed(request)
        val map = fetchMap(request, payload)
        return reconstruct(request, map, payload.pageIndex)
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
            val parsed = ProComicUtils.json.decodeFromString<ProComicMapProxyResponse>(
                readBoundedText(response, MAX_MAP_RESPONSE_BYTES),
            )
            if (parsed.success == false) {
                throw IOException("ProComic Reader: protected map response returned success=false")
            }
            parsed.data?.map ?: throw IOException("ProComic Reader: protected map response has no map")
        }
    }

    private fun reconstruct(
        pageRequest: Request,
        map: ProComicProtectedMap,
        pageIndex: Int,
    ): Response {
        val width = map.dim.getOrNull(0)?.takeIf { it in 1..MAX_BITMAP_DIMENSION }
            ?: throw IOException("ProComic Reader: protected map width is invalid")
        val height = map.dim.getOrNull(1)?.takeIf { it in 1..MAX_BITMAP_DIMENSION }
            ?: throw IOException("ProComic Reader: protected map height is invalid")
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
        try {
            order.forEachIndexed { outputIndex, sourceIndex ->
                val pieceUrl = map.pieces.getOrNull(sourceIndex)
                    ?.takeIf { ProComicUtils.isAllowedProtectedTileUrl(it) }
                    ?: throw IOException("ProComic Reader: protected tile URL is invalid")
                val rect = rects[outputIndex]
                val right = (rect.left.toLong() + rect.width.toLong())
                    .coerceIn(1L, width.toLong())
                    .toInt()
                val bottom = (rect.top.toLong() + rect.height.toLong())
                    .coerceIn(1L, height.toLong())
                    .toInt()
                val destination = Rect(
                    rect.left.coerceIn(0, width - 1),
                    rect.top.coerceIn(0, height - 1),
                    right,
                    bottom,
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
                    val bytes = readBoundedBytes(tileResponse, MAX_TILE_BYTES)
                    logUnexpectedTileMetadata(
                        bytes = bytes,
                        contentType = tileResponse.header("Content-Type"),
                        pageIndex = pageIndex,
                        outputIndex = outputIndex,
                        sourceIndex = sourceIndex,
                    )
                    val tile = decodeTile(
                        bytes = bytes,
                        pageIndex = pageIndex,
                        outputIndex = outputIndex,
                        sourceIndex = sourceIndex,
                        contentType = tileResponse.header("Content-Type"),
                    )
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
            val outputBytes = buffer.readByteArray()
            if (outputBytes.size > MAX_OUTPUT_BYTES) {
                throw IOException("ProComic Reader: reconstructed page output is too large")
            }
            val responseBody = outputBytes.toResponseBody(JPEG_MEDIA_TYPE)
            return Response.Builder()
                .request(pageRequest)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", JPEG_MEDIA_TYPE.toString())
                .body(responseBody)
                .build()
        } finally {
            result.recycle()
        }
    }

    private fun decodeTile(
        bytes: ByteArray,
        pageIndex: Int,
        outputIndex: Int,
        sourceIndex: Int,
        contentType: String?,
    ): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            validateTileDimensions(bounds.outWidth, bounds.outHeight)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    validateTileDimensions(info.size.width, info.size.height)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            }.getOrNull()?.let { return it }
        }

        runCatching { decodeWithAomedia(bytes) }
            .onFailure {
                ProComicDiag.logStage(
                    "PAGES",
                    96,
                    "AOMedia AVIF tile decode failed page=$pageIndex tile=$outputIndex source=$sourceIndex " +
                        "bytes=${bytes.size} contentType=${contentType ?: "(absent)"} " +
                        "errorType=${it.javaClass.simpleName}",
                )
            }
            .getOrNull()
            ?.let { return it }

        ProComicDiag.logStage(
            "PAGES",
            96,
            "AOMedia AVIF decoder returned no bitmap page=$pageIndex tile=$outputIndex source=$sourceIndex " +
                "bytes=${bytes.size} contentType=${contentType ?: "(absent)"}",
        )

        ProComicDiag.logStage(
            "PAGES",
            96,
            "protected tile decode failed page=$pageIndex tile=$outputIndex source=$sourceIndex " +
                "bytes=${bytes.size} contentType=${contentType ?: "(absent)"}",
        )
        throw IOException("ProComic Reader: protected tile could not be decoded")
    }

    private fun validateTileDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_TILE_PIXELS) {
            throw IOException("ProComic Reader: protected tile dimensions exceed limits")
        }
    }

    private fun decodeWithAomedia(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        val source = ByteBuffer.allocateDirect(bytes.size)
        source.put(bytes)
        source.flip()
        val info = AvifDecoder.Info()
        if (!AvifDecoder.getInfo(source, bytes.size, info)) return null
        val width = info.width
        val height = info.height
        if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_TILE_PIXELS) return null
        val config = if (info.depth > 8) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
        val bitmap = Bitmap.createBitmap(width, height, config)
        source.rewind()
        return if (AvifDecoder.decode(source, bytes.size, bitmap)) {
            bitmap
        } else {
            bitmap.recycle()
            null
        }
    }

    private fun logUnexpectedTileMetadata(
        bytes: ByteArray,
        contentType: String?,
        pageIndex: Int,
        outputIndex: Int,
        sourceIndex: Int,
    ) {
        val normalizedType = contentType?.substringBefore(';')?.trim()?.lowercase()
        val hasIsoBmffSignature = bytes.size >= 12 &&
            bytes[4] == 'f'.code.toByte() &&
            bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() &&
            bytes[7] == 'p'.code.toByte()
        if (normalizedType != "image/avif") {
            ProComicDiag.logStage(
                "PAGES",
                96,
                "protected tile response metadata unexpected page=$pageIndex tile=$outputIndex " +
                    "source=$sourceIndex bytes=${bytes.size} contentType=${contentType ?: "(absent)"}",
            )
        }
        if (!hasIsoBmffSignature) {
            ProComicDiag.logStage(
                "PAGES",
                96,
                "protected tile body signature invalid page=$pageIndex tile=$outputIndex " +
                    "source=$sourceIndex bytes=${bytes.size} contentType=${contentType ?: "(absent)"}",
            )
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
            val left = (width.toLong() * column.toLong() / actualColumns).toInt()
            val right = (width.toLong() * (column + 1).toLong() / actualColumns).toInt()
            val top = if (grid) (height.toLong() * row.toLong() / rows).toInt() else 0
            val bottom = if (grid) (height.toLong() * (row + 1).toLong() / rows).toInt() else height
            ProComicMapRect(left, top, right - left, bottom - top)
        }
    }

    private fun readBoundedText(response: Response, maxBytes: Int): String =
        readBoundedBytes(response, maxBytes).toString(StandardCharsets.UTF_8)

    private fun readBoundedBytes(response: Response, maxBytes: Int): ByteArray {
        val body = response.body ?: throw IOException("ProComic Reader: response body is missing")
        if (body.contentLength() > maxBytes) {
            throw IOException("ProComic Reader: response exceeds $maxBytes bytes")
        }
        val source = body.source()
        val buffer = Buffer()
        while (buffer.size <= maxBytes) {
            val remaining = maxBytes.toLong() + 1L - buffer.size
            val read = source.read(buffer, minOf(remaining, 16_384L))
            if (read == -1L) break
            if (buffer.size > maxBytes) {
                throw IOException("ProComic Reader: response exceeds $maxBytes bytes")
            }
        }
        return buffer.readByteArray()
    }

    private companion object {
        const val READER_BASE_URL = "https://procomic.pro"
        const val MAX_MAP_RESPONSE_BYTES = 1_000_000
        const val MAX_TILES = 32
        const val MAX_TILE_BYTES = 8_000_000
        const val MAX_TILE_PIXELS = 8_000_000L
        const val MAX_COMPOSITE_PIXELS = 40_000_000L
        const val MAX_BITMAP_DIMENSION = 16_384
        const val MAX_OUTPUT_BYTES = 32_000_000
        const val JPEG_QUALITY = 95
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
