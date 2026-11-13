package com.example.speechcoach

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSpeechOutputTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectsOnlyInstalledOfflineVoicesInTheDeviceLanguage() {
        val network = voice("network", Locale.US, network = true)
        val missing = voice("missing", Locale.US, features = setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
        val wrongLanguage = voice("french", Locale.FRANCE)
        val british = voice("british", Locale.UK)
        val american = voice("american", Locale.US)

        assertNull(selectOfflineVoice(listOf(network, missing, wrongLanguage), Locale.US, network))
        assertEquals(american, selectOfflineVoice(listOf(network, british, american), Locale.US, network))
        assertEquals(british, selectOfflineVoice(listOf(american, british), Locale.US, british))
    }

    @Test
    fun readsAndStopsSyntheticTextWithTheInstalledEngine() = runBlocking {
        assumeTrue(
            "Set speechCoachTtsProbe=true to test the device's installed offline voice.",
            InstrumentationRegistry.getArguments().getString("speechCoachTtsProbe") == "true",
        )
        compose.setContent { Text("Testing speech output") }
        val output = AndroidSpeechOutput(ApplicationProvider.getApplicationContext())

        withTimeout(60_000) {
            output.speak("This is a short test of local speech.")
        }
        val longSpeech = launch {
            output.speak("This is a longer test sentence. ".repeat(100))
        }
        delay(2_000)
        assertTrue(longSpeech.isActive)
        longSpeech.cancel()
        output.stop()
        withTimeout(10_000) { longSpeech.cancelAndJoin() }
        assertTrue(longSpeech.isCancelled)
        withTimeout(60_000) { output.speak("Speech can be started again.") }
    }

    private fun voice(
        name: String,
        locale: Locale,
        network: Boolean = false,
        features: Set<String> = emptySet(),
    ) = Voice(name, locale, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, network, features)
}
