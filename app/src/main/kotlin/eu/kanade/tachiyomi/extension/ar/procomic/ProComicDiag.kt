package eu.kanade.tachiyomi.extension.ar.procomic

import android.util.Log
import okhttp3.Response
import java.security.MessageDigest

/**
 * Production-safe runtime diagnostic helper for the ProComic parse pipeline.
 *
 * Diagnostics are deliberately metadata-only: request/response header values and raw response
 * bodies are never logged. Keep this redaction boundary intact when adding diagnostics.
 */
object ProComicDiag {

    const val TAG = "ProComicDiag"

    private const val SEP = "══════════════════════════════════════════════"
    private const val REDACTED = "<redacted>"

    private val sensitiveHeaderNames = setOf(
        "authorization",
        "cookie",
        "proxy-authorization",
        "set-cookie",
        "x-csrf-token",
        "csrf-token",
    )

    private val sensitiveQueryKey = Regex(
        "(?i)(?:^|[_-])(api[_-]?key|auth|authorization|code|csrf|key|nonce|pass(?:word)?|secret|session|sig(?:nature)?|token)(?:$|[_-])",
    )

    private val sensitiveAssignment = Regex(
        "(?i)(\\b(?:authorization|cookie|set-cookie|proxy-authorization|x-csrf-token|csrf|api[_-]?key|password|pass|secret|session(?:[_-]?id)?|token|signature|sig)\\b\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|'[^']*'|[^,;\\s}]+)",
    )

    private val bearerToken = Regex("(?i)\\bBearer\\s+[^\\s,;]+")
    private val absoluteUrl = Regex("https?://[^\\s\\\"'<>]+", RegexOption.IGNORE_CASE)

    fun sha256(s: String): String = try {
        MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "SHA256_ERROR"
    }

    /** Return only the URL path, removing all query and fragment material. */
    fun redactUrl(url: String): String = url
        .substringBefore('?')
        .substringBefore('#')
        .ifBlank { "(empty-url)" }

    /** Redact credentials, tokens, signed URLs, and URL-like values from free text. */
    fun sanitizeText(value: String?): String {
        if (value.isNullOrBlank()) return "(none)"
        var sanitized = value
        sanitized = absoluteUrl.replace(sanitized) { redactUrl(it.value) }
        sanitized = bearerToken.replace(sanitized, "Bearer $REDACTED")
        sanitized = sensitiveAssignment.replace(sanitized) { "${it.groupValues[1]}$REDACTED" }
        return sanitized.take(512)
    }

    /** Redact a header value while retaining the fact that the header was present. */
    fun redactHeaderValue(name: String, value: String): String =
        if (name.lowercase() in sensitiveHeaderNames) REDACTED else sanitizeText(value)

    private fun safeHeaderNames(response: Response): String =
        response.request.headers.names()
            .sorted()
            .joinToString(",")
            .ifBlank { "(none)" }

    private fun safeResponseHeader(response: Response, name: String): String =
        response.header(name)?.let { sanitizeText(it) } ?: "(absent)"

    // ── HTTP Response Logger ──────────────────────────────────────────────────

    /**
     * Log safe HTTP metadata only. Header names are retained for request-shape diagnostics,
     * while all header values and raw response body content are excluded or sanitized.
     */
    fun logResponse(tag: String, response: Response, body: String) {
        val safeUrl = redactUrl(response.request.url.toString())
        val bodyLen = body.length

        Log.d(TAG, SEP)
        Log.d(TAG, "[$tag] HTTP method=${response.request.method} url=$safeUrl")
        Log.d(TAG, "[$tag] status=${response.code} contentType=${safeResponseHeader(response, "Content-Type")}")
        Log.d(TAG, "[$tag] contentEncoding=${safeResponseHeader(response, "Content-Encoding")} " +
            "transferEncoding=${safeResponseHeader(response, "Transfer-Encoding")}")
        Log.d(TAG, "[$tag] contentLength=${safeResponseHeader(response, "Content-Length")} bodyLength=$bodyLen")
        Log.d(TAG, "[$tag] bodySha256=${sha256(body)}")
        Log.d(TAG, "[$tag] cache=${safeResponseHeader(response, "cf-cache-status")} " +
            "ray=${safeResponseHeader(response, "cf-ray")} " +
            "poweredBy=${safeResponseHeader(response, "x-powered-by")}")
        Log.d(TAG, "[$tag] requestHeaderNames=${safeHeaderNames(response)}")
        Log.d(TAG, SEP)
    }

    // ── Parser Stage Logger ────────────────────────────────────────────────────

    fun logStage(tag: String, stage: Int, message: String) {
        Log.d(TAG, "[$tag][S$stage] ${sanitizeText(message)}")
    }

    // ── Exception Logger ──────────────────────────────────────────────────────

    /** Log exception type and sanitized context without raw stack traces or body content. */
    fun logException(tag: String, stage: String, url: String, e: Throwable) {
        Log.e(TAG, "[$tag] exception stage=${sanitizeText(stage)} url=${redactUrl(url)}")
        Log.e(TAG, "[$tag] type=${e.javaClass.name} message=${sanitizeText(e.message)}")
        e.cause?.let { cause ->
            Log.e(TAG, "[$tag] causeType=${cause.javaClass.name}")
        }
    }
}
