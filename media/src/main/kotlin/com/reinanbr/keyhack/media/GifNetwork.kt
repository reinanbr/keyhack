package com.reinanbr.keyhack.media

/** Downloads [url] (HTTPS, a host in [allowedHosts], at most [maxBytes]). */
fun interface Fetcher {
    fun get(url: String, allowedHosts: Set<String>, maxBytes: Int): ByteArray
}

/**
 * Network access for GIF search. The :net module installs the fetcher at app
 * start in the online build; the offline build has none, and GIF search is
 * not offered.
 */
object GifNetwork {
    @Volatile
    var fetcher: Fetcher? = null

    val available: Boolean get() = fetcher != null
}
