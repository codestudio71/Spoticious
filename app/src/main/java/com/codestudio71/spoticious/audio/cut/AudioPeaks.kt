package com.codestudio71.spoticious.audio.cut

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioPeaks {
    suspend fun extract(
        context: Context,
        uri: Uri,
        bucketCount: Int = 900,
        onProgress: (Float) -> Unit = {},
    ): WaveformPeaks? =
        withContext(Dispatchers.IO) {
            PcmStreamDecoder.extractPeaks(context, uri, bucketCount, onProgress)
        }
}
