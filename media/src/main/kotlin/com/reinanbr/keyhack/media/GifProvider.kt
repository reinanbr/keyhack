package com.reinanbr.keyhack.media

import com.reinanbr.keyhack.core.Json
import com.reinanbr.keyhack.core.array
import com.reinanbr.keyhack.core.jsonArray
import com.reinanbr.keyhack.core.jsonObject
import com.reinanbr.keyhack.core.number
import com.reinanbr.keyhack.core.obj
import com.reinanbr.keyhack.core.string
import java.net.URLEncoder

data class GifMedia(val url: String, val width: Int, val height: Int)

data class GifItem(val id: String, val description: String, val preview: GifMedia, val full: GifMedia)

data class GifPage(val items: List<GifItem>, val next: String?)

/**
 * A GIF search service. Implementations only build URLs and parse responses;
 * [HttpFetcher] does the network I/O with a host allowlist.
 *
 * BYOK: the user supplies their own API key in settings. No key is ever
 * committed to the repository.
 */
interface GifProvider {
    val id: String

    /** Hosts this provider's API and media may be fetched from (suffix match). */
    val allowedHosts: Set<String>

    fun searchUrl(query: String, cursor: String?, limit: Int, locale: String): String

    fun trendingUrl(cursor: String?, limit: Int, locale: String): String

    fun parse(body: String): GifPage

    companion object {
        fun create(id: String, apiKey: String): GifProvider? {
            if (apiKey.isBlank()) return null
            return when (id) {
                GiphyProvider.ID -> GiphyProvider(apiKey.trim())
                TenorProvider.ID -> TenorProvider(apiKey.trim())
                else -> null
            }
        }
    }
}

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

// Giphy sends sizes as strings ("200"), Tenor as numbers.
private fun Map<String, Any?>.int(key: String): Int = number(key)?.toInt() ?: string(key)?.toIntOrNull() ?: 0

class GiphyProvider(private val apiKey: String) : GifProvider {
    override val id = ID
    override val allowedHosts = setOf("api.giphy.com", "giphy.com")

    override fun searchUrl(query: String, cursor: String?, limit: Int, locale: String) =
        "https://api.giphy.com/v1/gifs/search?api_key=${enc(apiKey)}&q=${enc(query)}&limit=$limit" +
            "&offset=${cursor?.toIntOrNull() ?: 0}&rating=pg-13&lang=${enc(locale.take(2))}&bundle=messaging_non_clips"

    override fun trendingUrl(cursor: String?, limit: Int, locale: String) =
        "https://api.giphy.com/v1/gifs/trending?api_key=${enc(apiKey)}&limit=$limit" +
            "&offset=${cursor?.toIntOrNull() ?: 0}&rating=pg-13&bundle=messaging_non_clips"

    override fun parse(body: String): GifPage {
        val root = Json.parse(body).jsonObject() ?: throw IllegalArgumentException("bad response")
        val items =
            root.array("data").orEmpty().mapNotNull { e ->
                val o = e.jsonObject() ?: return@mapNotNull null
                val images = o.obj("images") ?: return@mapNotNull null
                fun media(vararg names: String): GifMedia? = names.firstNotNullOfOrNull { n ->
                    images.obj(n)?.let { m -> m.string("url")?.let { GifMedia(it, m.int("width"), m.int("height")) } }
                }
                val preview = media(
                    "fixed_width_small",
                    "fixed_width_downsampled",
                    "fixed_width",
                ) ?: return@mapNotNull null
                val full = media("downsized", "fixed_width", "original") ?: return@mapNotNull null
                GifItem(o.string("id").orEmpty(), o.string("title").orEmpty(), preview, full)
            }
        val p = root.obj("pagination")
        val next = p?.let { (it.int("offset") + it.int("count")).takeIf { n -> n < it.int("total_count") }?.toString() }
        return GifPage(items, next)
    }

    companion object {
        const val ID = "giphy"
    }
}

class TenorProvider(private val apiKey: String) : GifProvider {
    override val id = ID
    override val allowedHosts = setOf("tenor.googleapis.com", "media.tenor.com", "tenor.com")

    override fun searchUrl(query: String, cursor: String?, limit: Int, locale: String) =
        "https://tenor.googleapis.com/v2/search?key=${enc(apiKey)}&client_key=keyhack&q=${enc(query)}" +
            "&limit=$limit&media_filter=gif,tinygif&contentfilter=medium&locale=${enc(locale)}" +
            (cursor?.let { "&pos=${enc(it)}" } ?: "")

    override fun trendingUrl(cursor: String?, limit: Int, locale: String) =
        "https://tenor.googleapis.com/v2/featured?key=${enc(apiKey)}&client_key=keyhack&limit=$limit" +
            "&media_filter=gif,tinygif&contentfilter=medium&locale=${enc(locale)}" +
            (cursor?.let { "&pos=${enc(it)}" } ?: "")

    override fun parse(body: String): GifPage {
        val root = Json.parse(body).jsonObject() ?: throw IllegalArgumentException("bad response")
        val items =
            root.array("results").orEmpty().mapNotNull { e ->
                val o = e.jsonObject() ?: return@mapNotNull null
                val formats = o.obj("media_formats") ?: return@mapNotNull null
                fun media(name: String): GifMedia? = formats.obj(name)?.let { m ->
                    val dims = m["dims"].jsonArray().orEmpty().map { (it as? Number)?.toInt() ?: 0 }
                    m.string("url")?.let { GifMedia(it, dims.getOrElse(0) { 0 }, dims.getOrElse(1) { 0 }) }
                }
                val preview = media("tinygif") ?: media("gif") ?: return@mapNotNull null
                val full = media("gif") ?: preview
                GifItem(o.string("id").orEmpty(), o.string("content_description").orEmpty(), preview, full)
            }
        return GifPage(items, root.string("next")?.takeIf { it.isNotEmpty() && it != "0" })
    }

    companion object {
        const val ID = "tenor"
    }
}
