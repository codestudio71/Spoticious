package com.codestudio71.spoticious.audio.cut

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object WavMusicExporter {

    fun saveToMusic(
        resolver: android.content.ContentResolver,
        src: File,
        displayFileName: String,
    ): Uri? {
        return try {
            val display = displayFileName.ifBlank { "Spoticious_cut.wav" }
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, display)
            values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MUSIC}/Spoticious",
                )
                values.put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            @Suppress("DEPRECATION")
            val collection =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            val uri = resolver.insert(collection, values) ?: return null
            val stream = resolver.openOutputStream(uri)
            if (stream == null) {
                resolver.delete(uri, null, null)
                return null
            }
            stream.use { os -> src.inputStream().use { it.copyTo(os) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    fun sanitizeFileName(label: String): String {
        val base =
            label
                .trim()
                .replace(Regex("""[/\\:*?"<>|]"""), "_")
                .take(120)
                .ifBlank { "Spoticious_cut_${System.currentTimeMillis()}" }
        return if (base.endsWith(".wav", ignoreCase = true)) base else "$base.wav"
    }
}
