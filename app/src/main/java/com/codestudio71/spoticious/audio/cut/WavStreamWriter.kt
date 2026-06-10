package com.codestudio71.spoticious.audio.cut

import com.codestudio71.spoticious.audio.record.VocalRecorder
import java.io.File
import java.io.RandomAccessFile

/** Strumieniowy zapis PCM WAV — nagłówek dopisywany w [close]. */
class WavStreamWriter(
    file: File,
    private val sampleRate: Int,
    private val bitsPerSample: Int,
    private val numChannels: Int,
) : AutoCloseable {

    private val raf = RandomAccessFile(file, "rw")
    private var pcmBytesWritten: Long = 0L

    init {
        raf.setLength(0)
        raf.write(ByteArray(WAV_HEADER_SIZE))
    }

    fun write(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        raf.write(pcmBytes)
        pcmBytesWritten += pcmBytes.size
    }

    override fun close() {
        raf.seek(0)
        VocalRecorder.writeStdPcmWaveHeader(
            raf,
            sampleRate,
            bitsPerSample,
            numChannels,
            pcmBytesWritten,
        )
        raf.close()
    }

    companion object {
        private const val WAV_HEADER_SIZE = 44
    }
}
