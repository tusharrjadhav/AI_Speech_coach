package com.example.speechcoach.transcription

import android.content.Context
import com.example.speechcoach.core.AudioClip
import com.example.speechcoach.core.SpeechTranscriber
import com.example.speechcoach.core.Transcript
import com.example.speechcoach.core.TranscriptSegment
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriber
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class MediaPipeTranscriber(
    private val context: Context,
    private val model: File,
) : SpeechTranscriber {
    override suspend fun transcribe(clip: AudioClip): Transcript = withContext(Dispatchers.IO) {
        require(model.isFile) { "Download the speech model before transcribing." }
        val options = AudioTranscriber.AudioTranscriberOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(model.absolutePath)
                    .setDelegate(Delegate.CPU)
                    .build()
            )
            .setSampleRateHz(16_000)
            .setEnableInterimResults(true)
            .setModelArchitecture(AudioTranscriber.ModelArchitecture.AUTO)
            .setCacheDir(context.cacheDir.absolutePath)
            .build()
        val segments = mutableListOf<TranscriptSegment>()
        AudioTranscriber.createFromOptions(context, options).use { transcriber ->
            transcriber.prewarm()
            RandomAccessFile(clip.path, "r").use { audio ->
                require(audio.length() > 44) { "The recording is empty." }
                audio.seek(44)
                val bytes = ByteArray(8_000 * 2)
                while (audio.filePointer < audio.length()) {
                    coroutineContext.ensureActive()
                    val remaining = (audio.length() - audio.filePointer).coerceAtMost(bytes.size.toLong()).toInt()
                    audio.readFully(bytes, 0, remaining)
                    require(remaining % 2 == 0) { "The recording contains incomplete audio samples." }
                    val samples = FloatArray(remaining / 2) { i ->
                        val value = (bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)
                        value.toShort() / 32768f
                    }
                    val result = transcriber.transcribe(samples, audio.filePointer == audio.length())
                    result.segments().filter { it.isFinal }.forEach {
                        segments.add(TranscriptSegment(it.transcript(), it.fromTimestampMs(), it.toTimestampMs()))
                    }
                }
                transcriber.finishStream().segments().filter { it.isFinal }.forEach {
                    segments.add(TranscriptSegment(it.transcript(), it.fromTimestampMs(), it.toTimestampMs()))
                }
            }
        }
        Transcript(segments.joinToString(" ") { it.text.trim() }.trim(), segments)
    }
}
