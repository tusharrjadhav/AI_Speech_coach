package com.example.speechcoach.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoachingHarnessTest {
    @Test
    fun promptIncludesGoalAndClearlyQualifiedEstimates() {
        val report = SpeechAnalyzer.analyze("Um, hello uh team.", 30_000)
        val prompt = CoachingHarness.prompt("Um, hello uh team.", report, CoachingGoal.PACE)

        assertContains(prompt, "Selected goal: Pace and pauses")
        assertContains(prompt, "without claiming to have heard the recording")
        assertContains(prompt, "Transcript word count: 4")
        assertContains(prompt, "Estimated words per minute over the full recording: 8")
        assertContains(prompt, "Possible fillers in the transcript: um: 1, uh: 1")
        assertContains(prompt, "including\nsilence")
        assertContains(prompt, "Transcript:\nUm, hello uh team.")
    }

    @Test
    fun promptDoesNotInventMissingPaceOrFillers() {
        val prompt = CoachingHarness.prompt("Hello.", SpeechAnalyzer.analyze("Hello.", 0), CoachingGoal.FILLERS)

        assertContains(prompt, "Estimated words per minute over the full recording: unavailable")
        assertContains(prompt, "Possible fillers in the transcript: um: 0, uh: 0")
        assertContains(prompt, "A zero filler count does not prove")
    }

    @Test
    fun returnsOnlySuggestionsWithExactTranscriptExcerpts() {
        val transcript = "Our launch was on time. Tomorrow we review feedback."
        val response = """
            <suggestion>
            <source>launch was on time</source>
            <advice>Emphasize the launch timing.</advice>
            </suggestion>
            <suggestion>
            <source>Tomorrow we review feedback</source>
            <advice>Try a pause before this next idea.</advice>
            </suggestion>
        """.trimIndent()

        assertEquals(
            "Excerpt: “launch was on time”\nSuggestion: Emphasize the launch timing.\n\n" +
                "Excerpt: “Tomorrow we review feedback”\nSuggestion: Try a pause before this next idea.",
            CoachingHarness.verifiedAdvice(response, transcript),
        )
    }

    @Test
    fun rejectsInventedExcerptsAndMalformedOrExtraOutput() {
        val transcript = "The launch was on time."
        val valid = "<suggestion><source>on time</source><advice>Emphasize timing.</advice></suggestion>"
        val invalidResponses = listOf(
            valid.replace("on time", "early"),
            valid.replace("on time", "On time"),
            valid.replace("Emphasize timing.", " "),
            valid.replace("</advice>", ""),
            "Here is your advice:\n$valid",
            "$valid\nUnverified additional advice",
            valid.repeat(3),
            "",
        )

        invalidResponses.forEach { response ->
            assertFailsWith<IllegalStateException> {
                CoachingHarness.verifiedAdvice(response, transcript)
            }
        }
    }
}
