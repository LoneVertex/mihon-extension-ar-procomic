package eu.kanade.tachiyomi.extension.ar.procomic

import android.util.Log
import okhttp3.Response
import java.security.MessageDigest

/**
 * INSTRUMENTATION-ONLY — remove before publishing to keiyoushi repo.
 *
 * Runtime diagnostic helper for the ProComic RSC parse pipeline.
 *
 * Filter logcat with:
 *   adb logcat -s ProComicDiag
 *
 * Stages logged:
 *   HTTP: URL, status, all request+response headers, body size, SHA-256, snippets
 *   Parser: key search, array extraction, deserialization, filter, item count
 *   Exceptions: type, message, full stack trace, stage, URL
 */
object ProComicDiag {

    const val TAG = "ProComicDiag"

    private const val SEP = "══════════════════════════════════════════════"

    // ── SHA-256 ──────────────────────────────────────────────────────────────

    fun sha256(s: String): String = try {
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "SHA256_ERROR:${e.message}"
    }

    // ── HTTP Response Logger ──────────────────────────────────────────────────

    /**
     * Log every observable HTTP property of [response] together with the
     * already-decoded [body] string.  Call immediately after
     * `response.body!!.string()` in each parse method.
     *
     * Captures:
     * - Request URL (exactly what OkHttp sent)
     * - HTTP status code
     * - All request headers (confirms RSC:1 presence)
     * - All response headers (Content-Type, Content-Encoding, cf-* etc.)
     * - Declared Content-Length header vs actual decoded body length (detects gzip)
     * - Body SHA-256 (compare against curl baseline: /tmp/ondevice_rsc.txt)
     * - Body first 500 chars (detect HTML vs RSC wire format vs Cloudflare page)
     * - Body last 200 chars
     */
    fun logResponse(tag: String, response: Response, body: String) {
        val url              = response.request.url.toString()
        val status           = response.code
        val contentType      = response.header("Content-Type")      ?: "(absent)"
        val contentEncoding  = response.header("Content-Encoding")  ?: "(absent)"
        val transferEncoding = response.header("Transfer-Encoding") ?: "(absent)"
        val contentLenHdr    = response.header("Content-Length")    ?: "(absent)"
        val cfCacheStatus    = response.header("cf-cache-status")   ?: "(absent)"
        val cfRay            = response.header("cf-ray")            ?: "(absent)"
        val xPoweredBy       = response.header("x-powered-by")      ?: "(absent)"
        val bodyLen          = body.length
        val bodyHash         = sha256(body)
        val bodyFirst500     = body.take(500).replace("\n", "↵").replace("\r", "")
        val bodyLast200      = body.takeLast(200).replace("\n", "↵").replace("\r", "")

        Log.d(TAG, SEP)
        Log.d(TAG, "[$tag] ── HTTP RESPONSE ──")
        Log.d(TAG, "[$tag] URL: $url")
        Log.d(TAG, "[$tag] Status: $status")
        Log.d(TAG, "[$tag] Content-Type: $contentType")
        Log.d(TAG, "[$tag] Content-Encoding: $contentEncoding")
        Log.d(TAG, "[$tag] Transfer-Encoding: $transferEncoding")
        Log.d(TAG, "[$tag] Content-Length (header): $contentLenHdr")
        Log.d(TAG, "[$tag] Body length (post-decompress): $bodyLen")
        Log.d(TAG, "[$tag] Body SHA-256: $bodyHash")
        Log.d(TAG, "[$tag] cf-cache-status: $cfCacheStatus")
        Log.d(TAG, "[$tag] cf-ray: $cfRay")
        Log.d(TAG, "[$tag] x-powered-by: $xPoweredBy")

        Log.d(TAG, "[$tag] ── REQUEST HEADERS ──")
        response.request.headers.forEach { (name, value) ->
            Log.d(TAG, "[$tag]   req> $name: $value")
        }

        Log.d(TAG, "[$tag] ── RESPONSE HEADERS ──")
        response.headers.forEach { (name, value) ->
            Log.d(TAG, "[$tag]   res> $name: $value")
        }

        Log.d(TAG, "[$tag] ── BODY SNIPPETS ──")
        Log.d(TAG, "[$tag] first500: $bodyFirst500")
        Log.d(TAG, "[$tag] last200:  $bodyLast200")
        Log.d(TAG, SEP)
    }

    // ── Parser Stage Logger ────────────────────────────────────────────────────

    fun logStage(tag: String, stage: Int, message: String) {
        Log.d(TAG, "[$tag][S$stage] $message")
    }

    // ── Exception Logger ──────────────────────────────────────────────────────

    /**
     * Log a caught exception with full context.  Does NOT rethrow — the caller
     * must still handle it (return emptyList, null, etc.) exactly as before.
     */
    fun logException(tag: String, stage: String, url: String, e: Throwable) {
        Log.e(TAG, "[$tag] *** EXCEPTION ***  stage=$stage")
        Log.e(TAG, "[$tag]   url=$url")
        Log.e(TAG, "[$tag]   type=${e.javaClass.name}")
        Log.e(TAG, "[$tag]   message=${e.message}")
        val cause = e.cause
        if (cause != null) {
            Log.e(TAG, "[$tag]   cause.type=${cause.javaClass.name}")
            Log.e(TAG, "[$tag]   cause.msg=${cause.message}")
        }
        Log.e(TAG, "[$tag]   stacktrace:", e)
    }
}
