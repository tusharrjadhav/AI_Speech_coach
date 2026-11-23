package com.example.speechcoach

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.speechcoach.core.CoachingGoal
import com.example.speechcoach.core.TranscriptComparer
import com.example.speechcoach.ui.PracticeActions
import com.example.speechcoach.ui.PracticeScreen
import com.example.speechcoach.ui.PracticeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptComparisonUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun choosesPracticeFocusAndDisablesItDuringAnOperation() {
        val state = mutableStateOf(PracticeState())
        compose.setContent {
            MaterialTheme {
                PracticeScreen(
                    state = state.value,
                    actions = PracticeActions(
                        record = {}, stop = {}, play = {}, delete = {}, openSettings = {}, transcribe = {},
                        editTranscript = {},
                        selectCoachingGoal = { state.value = state.value.copy(coachingGoal = it) },
                        coach = {}, improve = {}, readSuggestion = {}, stopSuggestion = {}, dismissError = {},
                    ),
                )
            }
        }
        compose.onNodeWithText("Pace and pauses").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Practice idea: ${CoachingGoal.PACE.practiceTip}")
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(busy = true) }
        compose.onNodeWithText("Filler words").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun switchesBetweenHighlightedVersionsWithoutReplacingTheOriginal() {
        val original = "We finished the launch on time."
        val improved = "We finished the launch early."
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    PracticeScreen(
                        state = PracticeState(
                            transcript = original,
                            coachModelReady = true,
                            suggestion = "Make the wording **clearer**.",
                            improvement = TranscriptComparer.compare(original, improved),
                        ),
                        actions = PracticeActions(
                            record = {}, stop = {}, play = {}, delete = {}, openSettings = {}, transcribe = {},
                            editTranscript = {}, selectCoachingGoal = {}, coach = {}, improve = {}, readSuggestion = {},
                            stopSuggestion = {}, dismissError = {},
                        ),
                    )
                }
            }
        }
        val comparison = compose.onNodeWithTag("transcript-comparison-text")
        val spoken = comparison.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(spoken.contains("Removed: on time"))
        assertTrue(spoken.contains("Added: early"))
        compose.onNodeWithText("Before").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("on time").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("After").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("early").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Make the wording clearer.").assertExists()
        compose.onNodeWithText("Original").performScrollTo().assertIsDisplayed().performClick()
        comparison.assertTextEquals(original)
        assertTrue(
            comparison.fetchSemanticsNode().config[SemanticsProperties.Text].single()
                .spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough }
        )
        compose.onNodeWithText("Improved").performScrollTo().assertIsDisplayed().performClick()
        comparison.assertTextEquals(improved)
    }

    @Test
    fun revealsAdditionalSuggestedEditsOnRequest() {
        val original = "one alpha two bravo three charlie four delta five echo"
        val improved = "one red two blue three green four yellow five violet"
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    PracticeScreen(
                        state = PracticeState(
                            transcript = original,
                            coachModelReady = true,
                            suggestion = "Use clearer words.",
                            improvement = TranscriptComparer.compare(original, improved),
                        ),
                        actions = PracticeActions(
                            record = {}, stop = {}, play = {}, delete = {}, openSettings = {}, transcribe = {},
                            editTranscript = {}, selectCoachingGoal = {}, coach = {}, improve = {}, readSuggestion = {},
                            stopSuggestion = {}, dismissError = {},
                        ),
                    )
                }
            }
        }
        compose.onAllNodesWithText("Before").assertCountEquals(3)
        compose.onNodeWithText("Show more changes (2 remaining)").performScrollTo().performClick()
        compose.onAllNodesWithText("Before").assertCountEquals(5)
        compose.onNodeWithText("violet").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun suggestionReadingControlsFollowStateAndDisplayErrors() {
        val state = mutableStateOf(PracticeState(suggestion = "Use **clearer** words."))
        var starts = 0
        var stops = 0
        var spokenText: String? = null
        compose.setContent {
            MaterialTheme {
                PracticeScreen(
                    state = state.value,
                    actions = PracticeActions(
                        record = {}, stop = {}, play = {}, delete = {}, openSettings = {}, transcribe = {},
                        editTranscript = {}, selectCoachingGoal = {}, coach = {}, improve = {},
                        readSuggestion = { text ->
                            starts++
                            spokenText = text
                            state.value = state.value.copy(readingSuggestion = true)
                        },
                        stopSuggestion = { stops++; state.value = state.value.copy(readingSuggestion = false) },
                        dismissError = {},
                    ),
                )
            }
        }
        compose.onNodeWithText("Listen to suggestions").performScrollTo().performClick()
        compose.onNodeWithText("Stop reading").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, stops)
            assertEquals("Use clearer words.", spokenText)
            state.value = state.value.copy(
                busy = true,
                suggestionSpeechError = "Install an offline voice in text-to-speech settings.",
            )
        }
        compose.onNodeWithText("Listen to suggestions").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Install an offline voice in text-to-speech settings.")
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(busy = false, recording = true) }
        compose.onNodeWithText("Listen to suggestions").performScrollTo().assertIsNotEnabled()
    }
}
