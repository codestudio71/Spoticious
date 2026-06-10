package com.codestudio71.spoticious.player

import android.app.Application
import android.content.ContentUris
import android.net.Uri
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

    private const val TAG = "MasterDataRepository"

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
    }

    /**
     * Uruchamia analizę jeśli brak wyniku w cache i nie ma aktywnego joba.
     */
    fun requestAnalysis(app: Application, uri: Uri, durationMs: Long, tooLongMessage: String) {
        val key = uriKey(uri)
        val map = _entries.value
        val existing = entryFor(uri, map)
        val jobActive = hasActiveJobForUri(uri)

        if (existing?.data != null) return
        if (jobActive) return

        val job = processScope.launch {
            try {
                if (durationMs > 10 * 60 * 1000L) {
                    mutate(key) {
                        it.copy(loading = false, error = tooLongMessage, progress = 0f, data = null)
                    }
                    return@launch
                }
                mutate(key) {
                    MasterDataEntry(loading = true, error = null, progress = 0f, data = null)
                }
                val result = try {
                    MasterDataAnalyzer.analyze(app, uri) { p ->
                        val clamped = p.coerceIn(0f, 1f)
                        mutate(key) { e -> e.copy(progress = clamped) }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "analyze failed for $uri", t)
                    null
                }
                mutate(key) { e ->
                    e.copy(loading = false, progress = 1f, data = result, error = e.error)
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                jobs.remove(key)
            }
        }
        jobs[key] = job
        job.invokeOnCompletion { cause ->
            if (cause != null && cause !is CancellationException) {
                Log.e(TAG, "analysis job failed for $uri", cause)
            }
        }
    }
}
