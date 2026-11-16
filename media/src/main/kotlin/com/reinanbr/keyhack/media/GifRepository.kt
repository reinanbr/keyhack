package com.reinanbr.keyhack.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.core.content.FileProvider
import com.reinanbr.keyhack.core.Json
import com.reinanbr.keyhack.core.jsonArray
import com.reinanbr.keyhack.core.jsonObject
import com.reinanbr.keyhack.core.number
import com.reinanbr.keyhack.core.string
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Loads GIF pages and images off the main thread. Previews are kept in a small
 * memory cache; a full GIF is downloaded only when the user sends it, into
 * cacheDir/gif (served to the target app through FileProvider).
 */
class GifRepository(context: Context) {
    private val app = context.applicationContext
    private val io: ExecutorService = Executors.newFixedThreadPool(THREADS)
    private val main = Handler(Looper.getMainLooper())
    private val previews =
        object : LruCache<String, ByteArray>(PREVIEW_CACHE_BYTES) {
            override fun sizeOf(key: String, value: ByteArray) = value.size
        }
    private val dir = File(app.cacheDir, "gif")
    private val prefs = app.getSharedPreferences("keyhack_gif", Context.MODE_PRIVATE)

    fun page(
        provider: GifProvider,
        query: String?,
        cursor: String?,
        locale: String,
        callback: (Result<GifPage>) -> Unit,
    ) {
        io.execute {
            val result =
                runCatching {
                    val url =
                        if (query.isNullOrBlank()) {
                            provider.trendingUrl(cursor, PAGE_SIZE, locale)
                        } else {
                            provider.searchUrl(query.trim(), cursor, PAGE_SIZE, locale)
                        }
                    val body = HttpFetcher(provider.allowedHosts).get(url, MAX_JSON_BYTES)
                    provider.parse(String(body, Charsets.UTF_8))
                }
            main.post { callback(result) }
        }
    }

    fun preview(provider: GifProvider?, url: String, callback: (ByteArray?) -> Unit) {
        previews.get(url)?.let {
            callback(it)
            return
        }
        val hosts = provider?.allowedHosts ?: ALL_HOSTS
        io.execute {
            val bytes = runCatching { HttpFetcher(hosts).get(url, MAX_PREVIEW_BYTES) }.getOrNull()
            if (bytes != null) previews.put(url, bytes)
            main.post { callback(bytes) }
        }
    }

    /** Downloads [item]'s full GIF and returns a content:// URI for commitContent. */
    fun download(provider: GifProvider?, item: GifItem, callback: (Result<Uri>) -> Unit) {
        val hosts = provider?.allowedHosts ?: ALL_HOSTS
        io.execute {
            val result =
                runCatching {
                    dir.mkdirs()
                    val file = File(dir, sha(item.full.url) + ".gif")
                    if (!file.isFile) {
                        val bytes = HttpFetcher(hosts).get(item.full.url, MAX_GIF_BYTES)
                        val tmp = File(dir, file.name + ".tmp")
                        tmp.writeBytes(bytes)
                        tmp.renameTo(file)
                    }
                    trim()
                    FileProvider.getUriForFile(app, app.packageName + ".media", file)
                }
            main.post { callback(result) }
        }
    }

    private fun trim() {
        val files = dir.listFiles().orEmpty().sortedByDescending { it.lastModified() }
        files.drop(MAX_FILES).forEach { it.delete() }
    }

    fun recents(): List<GifItem> = runCatching {
        Json.parse(prefs.getString(KEY_RECENTS, "[]").orEmpty()).jsonArray().orEmpty().mapNotNull { e ->
            val o = e.jsonObject() ?: return@mapNotNull null
            fun media(prefix: String): GifMedia? = o.string("${prefix}u")?.let {
                GifMedia(it, o.number("${prefix}w")?.toInt() ?: 0, o.number("${prefix}h")?.toInt() ?: 0)
            }
            val preview = media("p") ?: return@mapNotNull null
            val full = media("f") ?: return@mapNotNull null
            GifItem(o.string("id").orEmpty(), o.string("d").orEmpty(), preview, full)
        }
    }.getOrDefault(emptyList())

    fun addRecent(item: GifItem) {
        val list = (listOf(item) + recents().filter { it.full.url != item.full.url }).take(MAX_RECENTS)
        val json =
            list.map {
                mapOf(
                    "id" to it.id,
                    "d" to it.description,
                    "pu" to it.preview.url,
                    "pw" to it.preview.width,
                    "ph" to it.preview.height,
                    "fu" to it.full.url,
                    "fw" to it.full.width,
                    "fh" to it.full.height,
                )
            }
        prefs.edit().putString(KEY_RECENTS, Json.write(json)).apply()
    }

    fun clearCache() {
        previews.evictAll()
        io.execute { dir.deleteRecursively() }
    }

    fun close() {
        io.shutdownNow()
    }

    private fun sha(s: String): String = MessageDigest.getInstance(
        "SHA-256",
    ).digest(s.toByteArray()).take(HASH_BYTES).joinToString("") { "%02x".format(it) }

    companion object {
        const val PAGE_SIZE = 24
        private const val THREADS = 3
        private const val PREVIEW_CACHE_BYTES = 12 * 1024 * 1024
        private const val MAX_JSON_BYTES = 2 * 1024 * 1024
        private const val MAX_PREVIEW_BYTES = 3 * 1024 * 1024
        private const val MAX_GIF_BYTES = 15 * 1024 * 1024
        private const val MAX_FILES = 40
        private const val MAX_RECENTS = 30
        private const val HASH_BYTES = 12
        private const val KEY_RECENTS = "recents"
        private val ALL_HOSTS = GiphyProvider("").allowedHosts + TenorProvider("").allowedHosts
    }
}
