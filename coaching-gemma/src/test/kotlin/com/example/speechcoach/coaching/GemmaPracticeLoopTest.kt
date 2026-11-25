package com.example.speechcoach.coaching

import com.example.speechcoach.core.Attempt
import com.example.speechcoach.core.CoachingGoal
import com.example.speechcoach.core.PracticeContext
import com.example.speechcoach.core.Scenario
import com.example.speechcoach.core.SpeechAnalyzer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GemmaPracticeLoopTest {
    @Test
    fun retryPromptUsesPreviousExerciseButOnlyAcceptsEvidenceFromCurrentTake() = runTest {
        val before = "Um we finished early."
        val now = "We finished on time."
        val goal = CoachingGoal.CLARITY
        val baseline = Attempt("a", 0, Scenario.UPDATE, goal, 5_000, before, SpeechAnalyzer.analyze(before, 5_000),
            null, "Try separating the result from the next step.", null, null, null)
        var excerpt = "finished on time"
        val coach = GemmaCoach { prompt ->
            assertTrue(prompt.contains(baseline.exercise))
            assertTrue(prompt.contains("25.0 → 0.0"))
            assertFalse(prompt.contains(before))
            "<suggestion><source>$excerpt</source><advice>Lead with this result.</advice></suggestion>"
        }
        suspend fun suggest() = coach.suggest(now, SpeechAnalyzer.analyze(now, 5_000), goal,
            PracticeContext(Scenario.UPDATE, baseline))
        assertTrue(suggest().contains("Lead with this result."))
        excerpt = "finished early"
        val error = runCatching { suggest() }.exceptionOrNull()
        assertTrue("Reject advice that cites only the old take", error is IllegalStateException)
    }
}
