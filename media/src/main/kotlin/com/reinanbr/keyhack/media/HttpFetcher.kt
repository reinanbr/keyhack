package com.reinanbr.keyhack.media

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * The keyboard's only network client. HTTPS only, hosts restricted to the
 * active provider's allowlist (a response cannot redirect us elsewhere), size
 * capped, no cookies, no identifying headers.
 */
class HttpFetcher(private val allowedHosts: Set<String>) {
    class FetchException(message: String) : IOException(message)

    fun isAllowed(url: String): Boolean {
        val u = runCatching { URL(url) }.getOrNull() ?: return false
        if (u.protocol != "https" || u.userInfo != null) return false
        val host = u.host.lowercase()
        return allowedHosts.any { host == it || host.endsWith(".$it") }
    }

    fun get(url: String, maxBytes: Int): ByteArray {
        if (!isAllowed(url)) fail("blocked host")
        var current = url
        repeat(MAX_REDIRECTS + 1) {
            val conn = URL(current).openConnection() as HttpsURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", "KeyHack")
                conn.setRequestProperty("Accept-Language", "")
                val code = conn.responseCode
                if (code in REDIRECT_CODES) {
                    val location = conn.getHeaderField("Location") ?: fail("bad redirect")
                    current = URL(URL(current), location).toString()
                    if (!isAllowed(current)) fail("redirect to blocked host")
                    return@repeat
                }
                if (code != HttpsURLConnection.HTTP_OK) fail("HTTP $code")
                if (conn.contentLengthLong > maxBytes) fail("too large")
                return conn.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(BUFFER)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) fail("too large")
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
            } finally {
                conn.disconnect()
            }
        }
        fail("too many redirects")
    }

    private fun fail(message: String): Nothing = throw FetchException(message)

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val MAX_REDIRECTS = 3
        const val BUFFER = 16 * 1024
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
