package com.example.speechcoach

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.speechcoach.core.AudioClip
import com.example.speechcoach.transcription.MediaPipeTranscriber
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaAudioProbeTest {
    @Test
    fun understandsShortSyntheticRecording() {
        assumeTrue(
            "Set speechCoachAudioProbe=true to run the model-dependent probe.",
            InstrumentationRegistry.getArguments().getString("speechCoachAudioProbe") == "true",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.noBackupFilesDir, "models/${LocalModel.COACH.fileName}")
        val audio = File(context.cacheDir, "speechcoach-gemma-probe.wav")
        require(model.isFile && model.length() == LocalModel.COACH.size) {
            "Install the pinned Gemma model in Model settings before running the probe."
        }
        require(audio.isFile && audio.length() > 44) {
            "Install the synthetic probe WAV in the app cache before running the probe."
        }
        val speechModel = File(context.noBackupFilesDir, "models/${LocalModel.SPEECH.fileName}")
        require(speechModel.isFile && speechModel.length() == LocalModel.SPEECH.size) {
            "Install the pinned speech model in Model settings before running the probe."
        }

        val transcript = runBlocking {
            val durationMillis = (audio.length() - 44) * 1_000 / (16_000 * 2)
            MediaPipeTranscriber(context, speechModel).transcribe(AudioClip(audio.absolutePath, durationMillis))
        }.text
        Log.i(TAG, "Parakeet transcript: $transcript")
        assertTrue(
            "The synthetic probe transcript should include launch and feedback.",
            transcript.contains("launch", ignoreCase = true) &&
                transcript.contains("feedback", ignoreCase = true),
        )

        val engine = Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                audioBackend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath,
            )
        )
        val start = SystemClock.elapsedRealtime()
        engine.initialize()
        try {
            val transcription = engine.createConversation().use { conversation ->
                conversation.sendMessage(
                    Contents.of(
                        Content.AudioFile(audio.absolutePath),
                        Content.Text("Transcribe the speech in English. Return only the spoken words."),
                    )
                ).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()
            }
            Log.i(TAG, "Audio-only transcription: $transcription")
            val feedback = engine.createConversation().use { conversation ->
                conversation.sendMessage(
                    Contents.of(
                        Content.AudioFile(audio.absolutePath),
                        Content.Text(
                            "Listen to the recording. Give one short actionable suggestion to improve " +
                                "the wording, referring to a specific detail you heard. Do not invent " +
                                "measurements or judge the speaker's confidence."
                        ),
                    )
                ).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()
            }
            Log.i(TAG, "Audio-only coaching: $feedback")
            val combinedFeedback = engine.createConversation().use { conversation ->
                conversation.sendMessage(
                    Contents.of(
                        Content.AudioFile(audio.absolutePath),
                        Content.Text(
                            "Listen to the recording and consider the transcript below, which may contain " +
                                "recognition errors. Suggest one short, actionable improvement to the " +
                                "wording and refer to a specific detail. Do not invent measurements or " +
                                "judge the speaker's confidence.\n\nTranscript: $transcript"
                        ),
                    )
                ).contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()
            }
            Log.i(TAG, "Audio and transcript coaching: $combinedFeedback")
            Log.i(TAG, "Elapsed: ${SystemClock.elapsedRealtime() - start} ms")
            assertTrue("The audio coach returned an empty response.", feedback.isNotBlank())
            assertTrue("The audio and transcript coach returned an empty response.", combinedFeedback.isNotBlank())
            assertTrue(
                "The audio transcription should include team, tomorrow and feedback.",
                listOf("team", "tomorrow", "feedback").all { transcription.contains(it, ignoreCase = true) },
            )
        } finally {
            engine.close()
        }
    }

    private companion object {
        const val TAG = "GemmaAudioProbe"
    }
}
