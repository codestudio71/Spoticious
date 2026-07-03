package com.codestudio71.spoticious.audio.record

import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Stan nagrywania — po [stop] plik WAV jest zweryfikowany nagłówkiem od offsetu 0. */
sealed class RecordingState {
    data object Idle : RecordingState()

    data class Recording(val durationMs: Long, val peakAmplitude: Float) : RecordingState()

    data object Stopping : RecordingState()

    data class Saved(val file: File) : RecordingState()
}

private const val WAV_HEADER_SIZE = 44

private fun RandomAccessFile.closeQuietly() {
    try {
        close()
    } catch (_: Exception) {
    }
}

class VocalRecorder {

    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor + Dispatchers.IO)

    private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _state.asStateFlow()

    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var raf: RandomAccessFile? = null
    private var outputFile: File? = null
    private var active = false
    private var bitsPerSample = 16
    private var captureSampleRate = 48_000

    /** [SystemClock.elapsedRealtime] w momencie startu przechwytywania — do synchronizacji beatu w miksie. */
    @Volatile
    var captureStartElapsedMs: Long = 0L
        private set

    @Volatile var onWaveformFrame: ((ShortArray, Int) -> Unit)? = null

    @Volatile var onWaveformFrame24: ((ByteArray, Int) -> Unit)? = null

    /** Wywoływane (nie częściej niż co [MIC_SILENCE_TIMEOUT_MS]), gdy mikrofon przestał dostarczać dane. */
    @Volatile var onMicSignalLost: (() -> Unit)? = null

    /** Mono PCM ([AudioFormat.CHANNEL_IN_MONO]); jedna aktywna sesja naraz i jedno [preferredDevice]. */
    fun start(
        outputFile: File,
        sampleRate: Int,
        bitDepth: Int,
        preferredDevice: AudioDeviceInfo?,
    ): Boolean {
        synchronized(this) {
            val st = _state.value
            if (st is RecordingState.Recording || st is RecordingState.Stopping) return false
            if (active) return false
            active = true
        }

        captureSampleRate = sampleRate

        bitsPerSample = 16
        var audioEncoding = AudioFormat.ENCODING_PCM_16BIT
        val want24 =
            bitDepth >= 24 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (want24) {
            val mn =
                AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_24BIT_PACKED,
                )
            if (mn != AudioRecord.ERROR_BAD_VALUE && mn > 0) {
                audioEncoding = AudioFormat.ENCODING_PCM_24BIT_PACKED
                bitsPerSample = 24
            }
        }

        val bufferSize =
            AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, audioEncoding)
        if (bufferSize <= 0 || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            synchronized(this) { active = false }
            return false
        }
        val bufSize = bufferSize * 2

        return try {
            @Suppress("DEPRECATION")
            val recorder =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    audioEncoding,
                    bufSize,
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && preferredDevice != null) {
                try {
                    recorder.setPreferredDevice(preferredDevice)
                } catch (_: Exception) {
                }
            }
            recorder.startRecording()
            audioRecord = recorder

            this.outputFile = outputFile
            val outRaf =
                RandomAccessFile(outputFile, "rw").apply {
                    setLength(0)
                    seek(0)
                    write(ByteArray(WAV_HEADER_SIZE))
                }
            raf = outRaf

            recordJob =
                scope.launch {
                    val buffer = ByteArray(bufSize)
                    val waveformShortReuse = ShortArray(bufSize / 2)
                    val started = SystemClock.elapsedRealtime()
                    captureStartElapsedMs = started
                    var lastDataAtMs = started
                    _state.value = RecordingState.Recording(0L, 0f)
                    while (active && isActive) {
                        val ar = audioRecord ?: break
                        val n = ar.read(buffer, 0, buffer.size)
                        when {
                            n > 0 -> {
                                lastDataAtMs = SystemClock.elapsedRealtime()
                                var peakWritten = 0f
                                var durWritten = 0L
                                synchronized(outRaf) {
                                    outRaf.seek(outRaf.length())
                                    outRaf.write(buffer, 0, n)
                                    peakWritten = peakFromBuffer(buffer, n, bitsPerSample)
                                    durWritten = SystemClock.elapsedRealtime() - started
                                    _state.value =
                                        RecordingState.Recording(
                                            durationMs = durWritten,
                                            peakAmplitude = peakWritten,
                                        )
                                }
                                when (bitsPerSample) {
                                    24 -> {
                                        val samples24 = n / 3
                                        if (samples24 > 0) {
                                            onWaveformFrame24?.invoke(buffer, samples24)
                                        }
                                    }

                                    else -> {
                                        val sc = n shr 1
                                        if (sc > 0) {
                                            var o = 0
                                            while (o < sc && o < waveformShortReuse.size) {
                                                val lo = buffer[o * 2].toInt() and 0xff
                                                val hi = buffer[o * 2 + 1].toInt()
                                                waveformShortReuse[o] =
                                                    ((hi shl 8) or lo).toShort()
                                                o++
                                            }
                                            onWaveformFrame?.invoke(waveformShortReuse, sc)
                                        }
                                    }
                                }
                            }

                            n < 0 -> break

                            else -> {
                                // System może wyciszyć mikrofon (brak FGS / polityka OEM):
                                // timer nie może zamarzać, a UI dostaje sygnał utraty sygnału.
                                val nowMs = SystemClock.elapsedRealtime()
                                _state.value =
                                    RecordingState.Recording(
                                        durationMs = nowMs - started,
                                        peakAmplitude = 0f,
                                    )
                                if (nowMs - lastDataAtMs > MIC_SILENCE_TIMEOUT_MS) {
                                    onMicSignalLost?.invoke()
                                    lastDataAtMs = nowMs
                                }
                                delay(5)
                            }
                        }
                    }
                }
            true
        } catch (_: Throwable) {
            teardownAfterFailure()
            false
        }
    }

    fun stop(scopeCallback: ((File?) -> Unit)? = null) {
        synchronized(this) {
            if (!active) {
                scopeCallback?.invoke(null)
                return
            }
            active = false
        }

        _state.value = RecordingState.Stopping

        scope.launch {
            finalizeRecording(scopeCallback)
        }
    }

    fun release() {
        synchronized(this) {
            active = false
        }
        _state.value = RecordingState.Stopping
        runBlocking {
            finalizeRecording(null)
        }
        supervisor.cancel()
    }

    private suspend fun finalizeRecording(scopeCallback: ((File?) -> Unit)?) {
        try {
            recordJob?.cancelAndJoin()
        } catch (_: Exception) {
        }
        recordJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null

        val file = outputFile
        val rf = raf
        outputFile = null
        raf = null

        try {
            if (file != null && rf != null) {
                rf.fd.sync()
                val pcmLen = max(0L, rf.length() - WAV_HEADER_SIZE)
                if (pcmLen <= 0L) {
                    rf.closeQuietly()
                    file.delete()
                    _state.value = RecordingState.Idle
                    scopeCallback?.invoke(null)
                    return
                }
                rf.seek(0)
                writeStdPcmWaveHeader(rf, captureSampleRate, bitsPerSample, 1, pcmLen)
                rf.closeQuietly()
                _state.value = RecordingState.Saved(file)
                scopeCallback?.invoke(file)
                return
            }
        } catch (_: Exception) {
            try {
                rf?.closeQuietly()
            } catch (_: Exception) {
            }
            try {
                file?.delete()
            } catch (_: Exception) {
            }
        }

        _state.value = RecordingState.Idle
        scopeCallback?.invoke(null)
    }

    /** Przejście Saved → Idle po zapisie do MediaStore lub usunięciu pliku przez UI. */
    fun discardSavedToIdle() {
        if (_state.value is RecordingState.Saved) {
            _state.value = RecordingState.Idle
        }
    }

    private fun teardownAfterFailure() {
        try {
            recordJob?.cancel()
        } catch (_: Exception) {
        }
        recordJob = null
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        try {
            raf?.closeQuietly()
        } catch (_: Exception) {
        }
        raf = null
        synchronized(this) {
            active = false
        }
        _state.value = RecordingState.Idle
    }

    private fun peakFromBuffer(buffer: ByteArray, n: Int, bps: Int): Float =
        when (bps) {
            24 -> peak24Packed(buffer, n)
            else -> peak16(buffer, n)
        }

    private fun peak16(buffer: ByteArray, n: Int): Float {
        var maxAmp = 0
        var i = 0
        while (i + 1 < n) {
            val lo = buffer[i].toInt() and 0xff
            val hi = buffer[i + 1].toInt()
            val sample = (hi shl 8) or lo
            maxAmp = max(maxAmp, kotlin.math.abs(sample))
            i += 2
        }
        return min(1f, maxAmp / 32768f)
    }

    private fun peak24Packed(buffer: ByteArray, n: Int): Float {
        var maxAmp = 0
        var i = 0
        while (i + 2 < n) {
            val b0 = buffer[i].toInt() and 0xff
            val b1 = buffer[i + 1].toInt() and 0xff
            val b2 = buffer[i + 2].toInt()
            val s = (b0 or (b1 shl 8) or (b2 shl 16))
            val v = (s shl 8) shr 8
            maxAmp = max(maxAmp, v.absoluteValue)
            i += 3
        }
        return min(1f, maxAmp / 8_388_608f)
    }

    companion object {

        private const val MIC_SILENCE_TIMEOUT_MS = 1_000L

        internal fun writeStdPcmWaveHeader(
            out: RandomAccessFile,
            sampleRate: Int,
            bitsPerSample: Int,
            numChannels: Int,
            pcmDataLen: Long,
        ) {
            val blockAlign = numChannels * bitsPerSample / 8
            val byteRate = sampleRate * blockAlign
            val dataSize =
                pcmDataLen.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(0)

            fun writeFourCC(s: String) {
                require(s.length == 4)
                for (i in 0..3) {
                    val b = s[i].code
                    require(b in 0..255)
                    out.write(b)
                }
            }

            fun writeInt32Le(v: Int) {
                out.write(v and 0xff)
                out.write((v shr 8) and 0xff)
                out.write((v shr 16) and 0xff)
                out.write((v shr 24) and 0xff)
            }

            fun writeInt16Le(v: Short) {
                out.write(v.toInt() and 0xff)
                out.write((v.toInt() shr 8) and 0xff)
            }

            val riffChunkSize = 36 + dataSize
            writeFourCC("RIFF")
            writeInt32Le(riffChunkSize)
            writeFourCC("WAVE")
            writeFourCC("fmt ")
            writeInt32Le(16)
            writeInt16Le(1)
            writeInt16Le(numChannels.toShort())
            writeInt32Le(sampleRate)
            writeInt32Le(byteRate)
            writeInt16Le(blockAlign.toShort())
            writeInt16Le(bitsPerSample.toShort())
            writeFourCC("data")
            writeInt32Le(dataSize)
        }
    }
}
