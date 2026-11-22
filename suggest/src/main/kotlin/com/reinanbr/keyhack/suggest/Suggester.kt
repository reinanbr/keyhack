package com.reinanbr.keyhack.suggest

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import com.reinanbr.keyhack.suggest.grammar.GrammarChecker
import com.reinanbr.keyhack.suggest.grammar.GrammarFix
import com.reinanbr.keyhack.suggest.grammar.LanguageGrammar
import com.reinanbr.keyhack.suggest.grammar.Lexicon
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
    private val grammarCache = HashMap<String, LanguageGrammar>()
    private var grammar = GrammarChecker(emptyList())

    @Volatile private var grammarGeneration = 0

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
            grammar = GrammarChecker(
                languages.mapNotNull { lang ->
                    val dict = cache[lang] ?: return@mapNotNull null
                    grammarCache[lang] ?: loadGrammar(lang, dict).also { grammarCache[lang] = it }
                },
            )
            loadedLanguages = languages
            cache.keys.retainAll(languages.toSet())
            ngramCache.keys.retainAll(languages.toSet())
            grammarCache.keys.retainAll(languages.toSet())
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

    /**
     * The dictionary plus KeyHack's own lists: chat words ("vc", "lol"),
     * current words it lacks ("WhatsApp", "selfie") and common misspellings
     * corrected on space ("concerteza" -> "com certeza").
     */
    private fun load(language: String): Dictionary? = readAsset("dict/$language.dict") { main ->
        val extras = EXTRA_LISTS.mapNotNull { ext ->
            runCatching { app.assets.open("dict/$language.$ext").bufferedReader() }.getOrNull()
        }
        try {
            Dictionary.read(language, main, *extras.toTypedArray())
        } finally {
            extras.forEach { it.close() }
        }
    }

    private fun loadNgrams(language: String): NgramModel? = readAsset(
        "ngram/$language.ngram",
    ) { NgramModel.read(it, language) }

    private fun loadGrammar(language: String, dict: Dictionary): LanguageGrammar {
        val lexicon = Lexicon { w -> dict.lookup(w).let { if (it < 0) -1 else dict.freqs[it] } }
        val rules = runCatching { app.assets.open("grammar/$language.rules").bufferedReader() }.getOrNull()
        return LanguageGrammar.create(language, lexicon, rules)
    }

    /**
     * Checks the end of [textBeforeCursor] (just after a word was finished)
     * for a grammar or agreement mistake. Latest request wins.
     */
    fun checkGrammar(textBeforeCursor: String, callback: (GrammarFix?) -> Unit) {
        val gen = ++grammarGeneration
        worker.post {
            if (gen != grammarGeneration) return@post
            val fix = runCatching { grammar.check(textBeforeCursor) }.getOrNull()
            main.post { if (gen == grammarGeneration) callback(fix) }
        }
    }

    fun cancelGrammar() {
        grammarGeneration++
    }

    fun setKeyCenters(centers: Map<Char, Pair<Float, Float>>) {
        worker.post {
            engine.costs = EditCosts(centers)
            engine.glideDecoder.centers = centers
        }
    }

    /** Words for a glide over the letters, best first; latest request wins. */
    fun glide(path: GlidePath, previous: String?, before: String?, callback: (List<Candidate>) -> Unit) {
        val gen = ++generation
        worker.post {
            if (gen != generation) return@post
            val result = runCatching { engine.glide(path, previous, before) }.getOrDefault(emptyList())
            main.post { if (gen == generation) callback(result) }
        }
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
        val EXTRA_LISTS = listOf("chat", "extra", "fixes")
    }
}
