package com.example.speechcoach

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.speechcoach.core.AudioClip
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioRecorder(private val context: Context) {
    @Volatile private var recording = false

    fun start() {
        recording = true
    }

    fun stop() {
        recording = false
    }

    suspend fun record(): AudioClip = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Microphone permission is required to record.")
        }
        val minimum = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimum > 0) { "This device does not support 16 kHz microphone recording." }
        val output = File.createTempFile("recording-", ".wav", context.filesDir)
        var saved = false
        var recorder: AudioRecord? = null
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minimum, 8_192),
            )
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Could not initialize the microphone." }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Could not start the microphone." }
            var bytesWritten = 0
            RandomAccessFile(output, "rw").use { file ->
                file.write(ByteArray(44))
                val buffer = ByteArray(8_192)
                while (recording && bytesWritten < MAX_BYTES) {
                    val read = recorder.read(buffer, 0, minOf(buffer.size, MAX_BYTES - bytesWritten))
                    if (read < 0) throw IOException("Microphone read failed ($read).")
                    if (read > 0) {
                        file.write(buffer, 0, read)
                        bytesWritten += read
                    }
                }
                check(bytesWritten > 0 && bytesWritten % 2 == 0) { "No complete audio samples were recorded." }
                file.seek(0)
                file.write(wavHeader(bytesWritten))
            }
            saved = true
            AudioClip(output.absolutePath, bytesWritten * 1000L / (SAMPLE_RATE * 2))
        } finally {
            recording = false
            recorder?.release()
            if (!saved && output.exists() && !output.delete()) {
                android.util.Log.w("SpeechCoach", "Could not delete incomplete recording ${output.name}")
            }
        }
    }

    private fun wavHeader(length: Int): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(length + 36)
        buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(1)
        buffer.putInt(SAMPLE_RATE)
        buffer.putInt(SAMPLE_RATE * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(length)
        return buffer.array()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 120
        private const val MAX_BYTES = SAMPLE_RATE * 2 * MAX_SECONDS
    }
}
