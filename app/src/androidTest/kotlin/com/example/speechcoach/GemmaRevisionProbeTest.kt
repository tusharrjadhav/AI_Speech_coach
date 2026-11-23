package com.example.speechcoach

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.speechcoach.coaching.GemmaCoach
import com.example.speechcoach.core.CoachingGoal
import com.example.speechcoach.core.SpeechAnalyzer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GemmaRevisionProbeTest {
    @Test
    fun generatesSuggestionsWithVerifiedExcerptsForEachGoal() {
        assumeTrue(
            "Set speechCoachRevisionProbe=true to run the model-dependent probe.",
            InstrumentationRegistry.getArguments().getString("speechCoachRevisionProbe") == "true",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.noBackupFilesDir, "models/${LocalModel.COACH.fileName}")
        require(model.isFile && model.length() == LocalModel.COACH.size) {
            "Install the pinned Gemma model in Model settings before running the probe."
        }
        val original = "Um, our team completed the launch on time. Tomorrow we will review customer feedback."
        val withoutFillers = "Our team completed the launch on time. Tomorrow we will review customer feedback."
        val cases = CoachingGoal.entries.map { it to original } + listOf(
            CoachingGoal.FILLERS to withoutFillers,
            CoachingGoal.CLARITY to "The deadline is 5 May. We have three tasks remaining.",
            CoachingGoal.CLARITY to "Hi.",
            CoachingGoal.CLARITY to
                "Our team, uh, we completed the launch on time. Tomorrow we will review customer feedback.",
        )
        val coach = GemmaCoach(model, context.cacheDir)
        runBlocking {
            cases.forEach { (goal, transcript) ->
                val advice = coach.suggest(transcript, SpeechAnalyzer.analyze(transcript, 30_000), goal)
                Log.i(TAG, "${goal.label}, transcript: $transcript, advice: $advice")
                assertTrue("The suggestion should include a verified excerpt.", advice.contains("Excerpt:"))
            }
        }
    }

    @Test
    fun revisesSyntheticTranscriptUsingGeneratedAdvice() {
        assumeTrue(
            "Set speechCoachRevisionProbe=true to run the model-dependent probe.",
            InstrumentationRegistry.getArguments().getString("speechCoachRevisionProbe") == "true",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.noBackupFilesDir, "models/${LocalModel.COACH.fileName}")
        require(model.isFile && model.length() == LocalModel.COACH.size) {
            "Install the pinned Gemma model in Model settings before running the probe."
        }
        val original = "Our team completed the launch on time. Tomorrow we will review customer feedback."
        val coach = GemmaCoach(model, context.cacheDir)
        runBlocking {
            val advice = coach.suggest(original, SpeechAnalyzer.analyze(original, 5_000))
            val improved = coach.improve(original, advice)
            Log.i(TAG, "Advice: $advice")
            Log.i(TAG, "Improved: $improved")
            assertTrue("The rewritten transcript lost the launch timing.", improved.contains("on time", true))
            assertTrue("The rewritten transcript lost the feedback detail.", improved.contains("feedback", true))
            assertTrue("The rewritten transcript lost the review timing.", improved.contains("tomorrow", true))
        }
    }

    @Test
    fun keepsTheEndOfALongerSyntheticTranscript() {
        assumeTrue(
            "Set speechCoachRevisionProbe=true to run the model-dependent probe.",
            InstrumentationRegistry.getArguments().getString("speechCoachRevisionProbe") == "true",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.noBackupFilesDir, "models/${LocalModel.COACH.fileName}")
        require(model.isFile && model.length() == LocalModel.COACH.size) {
            "Install the pinned Gemma model in Model settings before running the probe."
        }
        val original = """
            Our team opened the community library on Monday after three months of preparation.
            We worked with local volunteers to organise the shelves and check the books.
            The first visitors arrived at ten in the morning, and the reading room filled up quickly.
            We received useful feedback about the signs near the entrance and the children's area.
            On Tuesday, we will add clearer signs and move a table closer to the window.
            We also plan to ask visitors which events they would like us to hold next month.
            The volunteers will meet again on Friday to discuss the responses.
            Finally, we will store the reports in the purple folder.
        """.trimIndent()
        val improved = runBlocking {
            GemmaCoach(model, context.cacheDir).improve(original, "Make the wording clearer without removing facts.")
        }
        Log.i(TAG, "Longer revision: $improved")
        assertTrue("The rewritten transcript lost its ending.", improved.contains("purple folder", true))
        assertTrue("The rewritten transcript lost the volunteer meeting.", improved.contains("Friday", true))
    }

    private companion object {
        const val TAG = "GemmaRevisionProbe"
    }
}
