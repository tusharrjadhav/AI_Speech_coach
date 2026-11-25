package com.example.speechcoach

import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.example.speechcoach.history.LocalPracticeHistory
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in because this journey operates on the real draft; run only on an empty disposable emulator. */
class PracticeLoopUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun click(label: String) = ui.onNodeWithText(label).performScrollTo().assertIsEnabled().performClick()
    private fun record(text: String) {
        click("Start recording")
        ui.onNodeWithText("Stop recording").assertExists()
        SystemClock.sleep(1500)
        click("Stop recording")
        ui.waitUntil(8000) { ui.onAllNodesWithText("Recording saved on this device.").fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasSetTextAction()).performScrollTo().performTextReplacement(text)
        Espresso.closeSoftKeyboard()
        ui.waitForIdle()
        click("Save attempt")
        try {
            ui.waitUntil(8000) { ui.onAllNodesWithText("Attempt saved.", substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            throw AssertionError(ui.onRoot().printToString(), e)
        }
    }

    @Test
    fun retryRetainsBothTakesAndAppearanceSurvivesRecreation() {
        val inst = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("speechCoachScratchDevice") == "true" &&
            android.os.Build.MODEL.contains("sdk", ignoreCase = true))
        val prefs = inst.targetContext.getSharedPreferences("practice", Context.MODE_PRIVATE)
        assumeTrue(!prefs.contains("recording"))
        assumeTrue(runBlocking { LocalPracticeHistory(inst.targetContext).use { it.list().isEmpty() } })
        click("Model settings")
        click("Light")
        ui.onNodeWithText("Light").assertIsSelected()
        click("System")
        ui.onNodeWithText("System").assertIsSelected()
        click("Dark")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithText("Dark").assertIsSelected()
        click("Back to practice")
        click("Interview")
        record("Um our team worked. Our team tested.")
        click("Local history")
        ui.onNodeWithText("Interview ·", substring = true).performClick()
        ui.onNodeWithText("Um our team worked. Our team tested.").assertExists()
        click("Play saved recording")
        ui.waitUntil(5000) { ui.onAllNodesWithText("Stop playback").fetchSemanticsNodes().isNotEmpty() }
        click("Practice this again")
        ui.onNodeWithText("Next recording: retry", substring = true).assertExists()
        record("Our team tested.")
        ui.onNodeWithText("Compared with your saved take").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("14.3 → 0.0", substring = true).assertExists()
        click("Local history")
        ui.onAllNodesWithText("Interview ·", substring = true).assertCountEquals(2)[0].performClick()
        ui.onNodeWithText("14.3 → 0.0", substring = true).assertExists()
        click("Delete saved attempt")
        ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithText("Our team tested.").assertExists()
        click("Delete saved attempt")
        ui.onNodeWithText("Delete from history").performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithText("Interview ·", substring = true).fetchSemanticsNodes().size == 1 }
        click("Back to practice")
        ui.onNode(hasSetTextAction()).assertTextContains("Our team tested.")
        click("Delete")
        ui.onNodeWithText("Recording deleted.").assertExists()
        click("Local history")
        ui.onNodeWithText("Interview ·", substring = true).performClick()
        ui.onNodeWithText("Um our team worked. Our team tested.").assertExists()
        click("Play saved recording")
        ui.waitUntil(5000) { ui.onAllNodesWithText("Stop playback").fetchSemanticsNodes().isNotEmpty() }
        click("Delete saved attempt")
        ui.onNodeWithText("Delete from history").performClick()
        ui.waitUntil(5000) { ui.onAllNodesWithText("No saved attempts.", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
}
