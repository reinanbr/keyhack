package com.reinanbr.keyhack.suggest

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Runs [SuggestionEngine] on a dedicated background thread so the main thread
 * never waits on dictionary work. Requests are "latest wins": a result is
 * delivered only if no newer request was made meanwhile.
 *
 * Learned data lives in noBackupFilesDir (never cloud-backed up) and is
 * written only from this thread.
 */
class Suggester(context: Context) {
    private val app = context.applicationContext
    private val thread = HandlerThread("keyhack-suggest", Process.THREAD_PRIORITY_DEFAULT).apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val historyFile = File(app.noBackupFilesDir, "learned.tsv")

    private val history = UserHistory()
    private val engine = SuggestionEngine(emptyList(), history)
    private val cache = HashMap<String, Dictionary>()
    private val ngramCache = HashMap<String, NgramModel>()

    @Volatile private var generation = 0
    private var loadedLanguages = emptyList<String>()

    init {
        worker.post {
            if (historyFile.isFile) runCatching { historyFile.bufferedReader().use(history::read) }
        }
    }

    /** Loads (or reuses) dictionaries for [languages], first = primary. */
    fun setLanguages(languages: List<String>) {
        worker.post {
            if (languages == loadedLanguages) return@post
            engine.dictionaries = languages.mapNotNull { lang -> cache[lang] ?: load(lang)?.also { cache[lang] = it } }
            engine.ngrams = languages.mapNotNull { lang ->
                ngramCache[lang] ?: loadNgrams(
                    lang,
                )?.also { ngramCache[lang] = it }
            }
            loadedLanguages = languages
            cache.keys.retainAll(languages.toSet())
            ngramCache.keys.retainAll(languages.toSet())
        }
    }

    // The repository keeps <name>.gz assets, but aapt un-gzips ".gz" assets
    // while packaging, so the APK holds the plain file (the zip compresses it
    // anyway). Read whichever exists.
    private fun <T> readAsset(path: String, parse: (BufferedReader) -> T): T? {
        val plain = runCatching { app.assets.open(path).bufferedReader().use(parse) }
        if (plain.isSuccess) return plain.getOrNull()
        return runCatching {
            app.assets.open("$path.gz").use { raw -> GZIPInputStream(raw, BUFFER).bufferedReader().use(parse) }
        }.getOrNull()
    }

    /** The dictionary plus the chat words people type but dictionaries lack ("vc", "lol"). */
    private fun load(language: String): Dictionary? = readAsset("dict/$language.dict") { main ->
        val chat = runCatching { app.assets.open("dict/$language.chat").bufferedReader() }.getOrNull()
        chat?.use { Dictionary.read(language, main, it) } ?: Dictionary.read(language, main)
    }

    private fun loadNgrams(language: String): NgramModel? = readAsset(
        "ngram/$language.ngram",
    ) { NgramModel.read(it, language) }

    fun setKeyCenters(centers: Map<Char, Pair<Float, Float>>) {
        worker.post { engine.costs = EditCosts(centers) }
    }

    fun suggest(typed: String, previous: String?, before: String?, callback: (Suggestions) -> Unit) {
        val gen = ++generation
        worker.post {
            if (gen != generation) return@post
            val result = engine.suggest(typed, previous, before = before)
            main.post { if (gen == generation) callback(result) }
        }
    }

    /**
     * Synchronous variant for the moment a word is committed and the async
     * result has not arrived yet. Waits at most [timeoutMs]; null on timeout.
     */
    fun suggestNow(
        typed: String,
        previous: String?,
        before: String?,
        timeoutMs: Long = SYNC_TIMEOUT_MS,
    ): Suggestions? {
        generation++
        val task = FutureTask { engine.suggest(typed, previous, before = before) }
        worker.postAtFrontOfQueue(task)
        return runCatching { task.get(timeoutMs, TimeUnit.MILLISECONDS) }.getOrNull()
    }

    fun predict(previous: String, before: String?, callback: (List<Candidate>) -> Unit) {
        val gen = ++generation
        worker.post {
            if (gen != generation) return@post
            val result = engine.predict(previous, before)
            main.post { if (gen == generation) callback(result) }
        }
    }

    /** Invalidates pending results (e.g. the word was committed). */
    fun cancel() {
        generation++
    }

    private var unsaved = 0

    fun learn(word: String, previous: String?) {
        worker.post {
            history.learn(word, previous)
            // Android may kill the keyboard process at any time: persist in small batches.
            if (++unsaved >= SAVE_EVERY) writeHistory()
        }
    }

    fun accept(word: String) {
        worker.post { history.accept(word) }
    }

    fun clearHistory() {
        worker.post {
            history.clear()
            historyFile.delete()
        }
    }

    /** Persists learned data if it changed. */
    fun save() {
        worker.post { writeHistory() }
    }

    private fun writeHistory() {
        unsaved = 0
        if (!history.dirty) return
        val tmp = File(historyFile.path + ".tmp")
        runCatching {
            tmp.bufferedWriter().use(history::write)
            tmp.renameTo(historyFile)
        }
    }

    fun close() {
        save()
        thread.quitSafely()
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val SYNC_TIMEOUT_MS = 120L
        const val SAVE_EVERY = 20
    }
}
