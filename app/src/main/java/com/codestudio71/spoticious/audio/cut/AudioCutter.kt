package com.codestudio71.spoticious.audio.cut

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioCutter {

    suspend fun exportSegment(
        context: Context,
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        outFile: File,
        sampleRate: Int,
        channelCount: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            WavStreamWriter(
                file = outFile,
                sampleRate = sampleRate,
                bitsPerSample = PcmStreamDecoder.EXPORT_BITS_PER_SAMPLE,
                numChannels = channelCount.coerceAtLeast(1),
            ).use { writer ->
                PcmStreamDecoder.exportWindow(context, sourceUri, startMs, endMs, writer)
            }
        }

    fun buildOutputFile(filesDir: File, label: String): File {
        val safe = sanitizeLabel(label)
        val stamp = System.currentTimeMillis()
        return File(filesDir, "Spoticious_cut_${safe}_$stamp.wav")
    }

    suspend fun exportMergedSegments(
        context: Context,
        sourceUri: Uri,
        windows: List<Pair<Long, Long>>,
        outFile: File,
        sampleRate: Int,
        channelCount: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (windows.size < 2) return@withContext false
            WavStreamWriter(
                file = outFile,
                sampleRate = sampleRate,
                bitsPerSample = PcmStreamDecoder.EXPORT_BITS_PER_SAMPLE,
                numChannels = channelCount.coerceAtLeast(1),
            ).use { writer ->
                // Kolejność = kolejność listy (zaznaczenia), bez sortowania po czasie
                for ((startMs, endMs) in windows) {
                    if (endMs <= startMs) return@withContext false
                    if (!PcmStreamDecoder.exportWindow(context, sourceUri, startMs, endMs, writer)) {
                        return@withContext false
                    }
                }
            }
            outFile.exists() && outFile.length() > 44
        }

    private fun sanitizeLabel(label: String): String =
        label
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(40)
            .ifEmpty { "fragment" }
}
