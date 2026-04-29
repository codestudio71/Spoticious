package com.codestudio71.spoticious.player

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.codestudio71.spoticious.SpoticiousApplication
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Analiza Master Data w tle — [processScope] pochodzi z [SpoticiousApplication]
 * (nie od Activity / ViewModel), żeby uniknąć przypadkowego anulowania przy nawigacji.
 */
data class MasterDataEntry(
    val progress: Float = 0f,
    val loading: Boolean = false,
    val data: MasterData? = null,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

object MasterDataRepository {

    private const val MAX_CACHED_KEYS = 10

    /** Gdy Application jeszcze nie gotowe (testy / edge) — izolowany scope. */
    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val processScope: CoroutineScope
        get() = SpoticiousApplication.instance?.masterDataScope ?: fallbackScope

    private val jobs = ConcurrentHashMap<String, Job>()

    private val _entries = MutableStateFlow<Map<String, MasterDataEntry>>(emptyMap())
    val entries: StateFlow<Map<String, MasterDataEntry>> = _entries.asStateFlow()

    private fun uriKey(uri: Uri) = uri.toString()

    private fun cancelAndRemoveJob(key: String, reason: String) {
        val j = jobs.remove(key) ?: return
        val stack = Throwable("MDRepo cancel caller trace: $reason").stackTraceToString().take(1200)
        Log.w(
            "MDRepo",
            "[${System.currentTimeMillis()}] job CANCEL requested key=$key reason=$reason\n$stack"
        )
        j.cancel(CancellationException(reason))
    }

    /** Ten sam utwór w MediaStore mimo różnych stringów URI (jak R3). */
    private fun keysMatchUri(storedKey: String, uri: Uri): Boolean {
        if (storedKey == uri.toString()) return true
        val idUri = try { ContentUris.parseId(uri) } catch (_: Exception) { return false }
        val idKey = try { ContentUris.parseId(Uri.parse(storedKey)) } catch (_: Exception) { return false }
        return idUri == idKey
    }

    /**
     * Wpis dla bieżącego utworu: najpierw dokładny klucz, potem dopasowanie po [ContentUris.parseId].
     */
    fun entryFor(uri: Uri?, entries: Map<String, MasterDataEntry>): MasterDataEntry? {
        if (uri == null) return null
        entries[uri.toString()]?.let { return it }
        val id = try { ContentUris.parseId(uri) } catch (_: Exception) { return null }
        var best: MasterDataEntry? = null
        for ((k, v) in entries) {
            if (runCatching { ContentUris.parseId(Uri.parse(k)) == id }.getOrDefault(false)) {
                if (best == null || v.updatedAt > best.updatedAt) best = v
            }
        }
        return best
    }

    private fun hasActiveJobForUri(uri: Uri): Boolean =
        jobs.any { (k, j) -> j.isActive && keysMatchUri(k, uri) }

    private fun mutate(key: String, block: (MasterDataEntry) -> MasterDataEntry) {
        _entries.update { map ->
            val m = map.toMutableMap()
            val old = m[key] ?: MasterDataEntry()
            m[key] = block(old).copy(updatedAt = System.currentTimeMillis())
            evictExcess(m)
            m
        }
    }

    private fun evictExcess(m: MutableMap<String, MasterDataEntry>) {
        while (m.size > MAX_CACHED_KEYS) {
            val victim = m.entries
                .filter { !it.value.loading }
                .minByOrNull { it.value.updatedAt }
                ?.key
                ?: break
            m.remove(victim)
            cancelAndRemoveJob(victim, "evictExcess LRU non-loading victim")
        }
    }

    fun remove(uri: Uri) {
        val keysToRemove = _entries.value.keys.filter { keysMatchUri(it, uri) }.toSet()
        for (k in keysToRemove) {
            cancelAndRemoveJob(k, "MasterDataRepository.remove(uri=$uri) key=$k")
        }
        if (keysToRemove.isEmpty()) {
            val k = uriKey(uri)
            cancelAndRemoveJob(k, "MasterDataRepository.remove fallback key=$k")
            _entries.update { it - k }
        } else {
            _entries.update { m -> m - keysToRemove }
        }
        Log.d("MDRepo", "remove: uri=$uri removedKeys=$keysToRemove")
    }

    /**
     * Uruchamia analizę jeśli brak wyniku w cache i nie ma aktywnego joba.
     */
    fun requestAnalysis(app: Application, uri: Uri, durationMs: Long, tooLongMessage: String) {
        val key = uriKey(uri)
        val map = _entries.value
        val existing = entryFor(uri, map)
        val jobActive = hasActiveJobForUri(uri)
        val tsReq = System.currentTimeMillis()
        Log.d(
            "MDRepo",
            "[$tsReq] requestAnalysis: uri=$uri, key=$key, alreadyRunning=$jobActive, " +
                "hasData=${existing?.data != null}, hasLoading=${existing?.loading == true}, " +
                "jobs.containsKey(key)=${jobs.containsKey(key)}, " +
                "scope=${if (SpoticiousApplication.instance != null) "Application" else "fallback"}"
        )

        if (existing?.data != null) return
        if (jobActive) return

        val t0 = SystemClock.elapsedRealtime()

        val job = processScope.launch {
            val tsStart = System.currentTimeMillis()
            Log.d("MDRepo", "[$tsStart] job STARTED: $uri (key=$key)")
            try {
                if (durationMs > 10 * 60 * 1000L) {
                    mutate(key) {
                        it.copy(loading = false, error = tooLongMessage, progress = 0f, data = null)
                    }
                    Log.d("MDRepo", "[${System.currentTimeMillis()}] entries emission: size=${_entries.value.size}")
                    Log.d(
                        "MDRepo",
                        "[${System.currentTimeMillis()}] analysis DONE (too long): $uri, took=${SystemClock.elapsedRealtime() - t0}ms"
                    )
                    return@launch
                }
                mutate(key) {
                    MasterDataEntry(loading = true, error = null, progress = 0f, data = null)
                }
                Log.d("MDRepo", "[${System.currentTimeMillis()}] entries emission: size=${_entries.value.size}")
                val result = try {
                    MasterDataAnalyzer.analyze(app, uri) { p ->
                        val clamped = p.coerceIn(0f, 1f)
                        Log.d("MDRepo", "[${System.currentTimeMillis()}] progress: $uri = $clamped")
                        mutate(key) { e -> e.copy(progress = clamped) }
                    }
                } catch (t: Throwable) {
                    Log.e("MDRepo", "[${System.currentTimeMillis()}] analyze failed for $uri", t)
                    null
                }
                mutate(key) { e ->
                    e.copy(loading = false, progress = 1f, data = result, error = e.error)
                }
                Log.d("MDRepo", "[${System.currentTimeMillis()}] entries emission: size=${_entries.value.size}")
                Log.d("MDRepo", "[${System.currentTimeMillis()}] analysis DONE: $uri, took=${SystemClock.elapsedRealtime() - t0}ms")
            } catch (e: CancellationException) {
                val trace = Throwable("MDRepo cancellation stack").stackTraceToString().take(1500)
                Log.d("MDRepo", "[${System.currentTimeMillis()}] job CANCELLED (catch): $uri, cause=$e\n$trace")
                throw e
            } finally {
                jobs.remove(key)
            }
        }
        jobs[key] = job
        job.invokeOnCompletion { cause ->
            val ts = System.currentTimeMillis()
            when (cause) {
                null -> Log.d("MDRepo", "[$ts] job COMPLETED (invokeOnCompletion): $uri")
                is CancellationException -> {
                    val trace = cause.stackTraceToString().take(800)
                    Log.d("MDRepo", "[$ts] job CANCELLED (invokeOnCompletion): $uri, cause=$cause trace=$trace")
                }
                else -> Log.e("MDRepo", "[$ts] job FAILED (invokeOnCompletion): $uri", cause)
            }
        }
    }
}
