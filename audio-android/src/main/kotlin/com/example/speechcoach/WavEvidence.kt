package com.example.speechcoach

import com.example.speechcoach.core.AudioClip
import com.example.speechcoach.core.PcmAnalyzer
import com.example.speechcoach.core.RecordingEvidence
import java.io.DataInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only for WAVs produced by AudioRecorder (44-byte header, mono little-endian PCM16). */
suspend fun recordingEvidence(clip: AudioClip): RecordingEvidence = withContext(Dispatchers.IO) {
    val file = File(clip.path)
    val length = file.length() - 44
    require(length in 2..3_840_000L && length % 2 == 0L) { "Incomplete practice recording." }
    val analyzer = PcmAnalyzer(AudioRecorder.SAMPLE_RATE)
    DataInputStream(file.inputStream().buffered()).use { input ->
        input.readFully(ByteArray(44))
        val bytes = ByteArray(16_384)
        var remaining = length
        while (remaining > 0) {
            val size = minOf(remaining, bytes.size.toLong()).toInt()
            input.readFully(bytes, 0, size)
            for (i in 0 until size step 2)
                analyzer.add(((bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)).toShort())
            remaining -= size
        }
    }
    analyzer.finish()
}
