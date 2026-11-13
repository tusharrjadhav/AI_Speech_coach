package com.example.speechcoach

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.speechcoach.core.TranscriptComparer
import com.example.speechcoach.ui.PracticeActions
import com.example.speechcoach.ui.PracticeScreen
import com.example.speechcoach.ui.PracticeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranscriptComparisonUiTest {
    @get:Rule
    val compose = createComposeRule()

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
                            suggestion = "Make the wording clearer.",
                            improvement = TranscriptComparer.compare(original, improved),
                        ),
                        actions = PracticeActions(
                            record = {}, stop = {}, play = {}, delete = {}, openSettings = {}, transcribe = {},
                            editTranscript = {}, coach = {}, improve = {}, dismissError = {},
                        ),
                    )
                }
            }
        }
        val comparison = compose.onNodeWithTag("transcript-comparison-text")
        val changeText = comparison.fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(changeText.text.contains("on time"))
        assertTrue(changeText.text.contains("early"))
        assertTrue(
            changeText.spanStyles.any { it.item.textDecoration == TextDecoration.Underline }
        )
        assertTrue(
            changeText.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough }
        )
        compose.onNodeWithText("Original").performScrollTo().assertIsDisplayed().performClick()
        comparison.assertTextEquals(original)
        assertTrue(
            comparison.fetchSemanticsNode().config[SemanticsProperties.Text].single()
                .spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough }
        )
        compose.onNodeWithText("Improved").performScrollTo().assertIsDisplayed().performClick()
        comparison.assertTextEquals(improved)
    }
}
