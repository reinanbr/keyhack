package com.reinanbr.keyhack.suggest

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
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
            loadedLanguages = languages
            cache.keys.retainAll(languages.toSet())
        }
    }

    // The repository keeps dict/<lang>.dict.gz, but aapt un-gzips ".gz" assets
    // while packaging, so the APK holds dict/<lang>.dict (the zip compresses it
    // anyway). Read whichever exists.
    private fun load(language: String): Dictionary? {
        val plain = runCatching {
            app.assets.open("dict/$language.dict").bufferedReader().use { Dictionary.read(language, it) }
        }
        if (plain.isSuccess) return plain.getOrNull()
        return runCatching {
            app.assets.open("dict/$language.dict.gz").use { raw ->
                GZIPInputStream(raw, BUFFER).bufferedReader().use { Dictionary.read(language, it) }
            }
        }.getOrNull()
    }

    fun setKeyCenters(centers: Map<Char, Pair<Float, Float>>) {
        worker.post { engine.costs = EditCosts(centers) }
    }

    fun suggest(typed: String, previous: String?, callback: (Suggestions) -> Unit) {
        val gen = ++generation
        worker.post {
            if (gen != generation) return@post
            val result = engine.suggest(typed, previous)
            main.post { if (gen == generation) callback(result) }
        }
    }

    /**
     * Synchronous variant for the moment a word is committed and the async
     * result has not arrived yet. Waits at most [timeoutMs]; null on timeout.
     */
    fun suggestNow(typed: String, previous: String?, timeoutMs: Long = SYNC_TIMEOUT_MS): Suggestions? {
        generation++
        val task = FutureTask { engine.suggest(typed, previous) }
        worker.postAtFrontOfQueue(task)
        return runCatching { task.get(timeoutMs, TimeUnit.MILLISECONDS) }.getOrNull()
    }

    fun predict(previous: String, callback: (List<Candidate>) -> Unit) {
        val gen = ++generation
        worker.post {
            if (gen != generation) return@post
            val result = engine.predict(previous)
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
