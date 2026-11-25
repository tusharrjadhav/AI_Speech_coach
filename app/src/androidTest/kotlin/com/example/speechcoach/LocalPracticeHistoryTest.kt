package com.example.speechcoach

import androidx.test.platform.app.InstrumentationRegistry
import com.example.speechcoach.core.*
import com.example.speechcoach.history.LocalPracticeHistory
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalPracticeHistoryTest {
    @Test
    fun reopensAnIndependentAudioCopyLargerThanACursorWindowAndDeletesIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "history-test-${UUID.randomUUID()}.db"
        val source = File.createTempFile("test-", ".wav", context.cacheDir)
        val bytes = ByteArray(3_840_044)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(bytes.size - 8); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(16_000); putInt(32_000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(bytes.size - 44)
        }
        val clip = AudioClip(source.path, 120_000)
        val attempt = Attempt("first", 1000L, Scenario.INTERVIEW, CoachingGoal.CLARITY, clip.durationMillis,
            "A clear test.", SpeechAnalyzer.analyze("A clear test.", clip.durationMillis),
            RecordingEvidence(listOf(QuietGap(1000, 1600)), false, false), "Practice the ending.", "AI advice", "A revision.", null)
        try {
            source.writeBytes(bytes)
            LocalPracticeHistory(context, name).use { it.save(attempt.copy(advice = null), clip); it.save(attempt, clip) }
            assertTrue(source.delete())
            LocalPracticeHistory(context, name).use { db ->
                assertEquals(listOf("first"), db.list().map { it.id })
                assertEquals(attempt, db.get("first"))
                val restored = db.readAudio("first")
                assertArrayEquals(bytes, restored)
                val ready = CountDownLatch(1)
                val error = AtomicReference<Exception?>()
                val playback = RecordingPlayback({ playing -> if (playing) ready.countDown() }, { error.set(it); ready.countDown() })
                val inst = InstrumentationRegistry.getInstrumentation()
                try {
                    inst.runOnMainSync { playback.play(restored, startMillis = 100_000) }
                    assertTrue("Timed out starting the restored WAV", ready.await(8, TimeUnit.SECONDS))
                    error.get()?.let { throw AssertionError("Restored WAV is not playable", it) }
                } finally {
                    inst.runOnMainSync { playback.stop() }
                }
                db.delete("first")
                assertTrue(db.list().isEmpty())
                assertNull(db.get("first"))
            }
        } finally {
            source.delete()
            context.deleteDatabase(name)
        }
        Unit
    }
}
