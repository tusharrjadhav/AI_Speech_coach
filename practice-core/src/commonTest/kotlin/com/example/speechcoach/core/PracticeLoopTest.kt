package com.example.speechcoach.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeLoopTest {
    @Test
    fun countsDensityAndRepeatedWordPairsWithoutSubstringMatches() {
        val result = SpeechAnalyzer.analyze("Um, our team worked. Our team used UHF.", 30_000)
        assertEquals(12.5, result.fillersPer100)
        assertEquals(mapOf("our team" to 2), result.repeatedPhrases)
        assertEquals(listOf("Um, our team worked. Our", "team worked. Our team used UHF"),
            SpeechAnalyzer.phraseExamples("Um, our team worked. Our team used UHF.", "our team"))
        assertNull(SpeechAnalyzer.analyze("", 0).fillersPer100)
    }

    @Test
    fun coarseRateRequiresMatchingTextAndDisjointTimedSections() {
        val a = TranscriptSegment("one two three four", 0, 4000)
        val b = TranscriptSegment("five six seven", 4000, 10_000)
        val text = "${a.text} ${b.text}"
        fun analyze(t: String, last: TranscriptSegment) = SpeechAnalyzer.analyze(t, 10_000, listOf(a, last)).sections
        assertEquals(listOf(60, 30), analyze(text, b).map { it.wordsPerMinute })
        assertTrue(analyze("Edited $text", b).isEmpty())
        assertTrue(analyze(text, b.copy(startMillis = 3000)).isEmpty())
    }

    @Test
    fun measuresInteriorQuietGapsButNotRoomToneAtTheEnds() {
        val a = PcmAnalyzer(16_000)
        fun samples(ms: Int, level: Short) = repeat(ms * 16) { a.add(level) }
        samples(1000, 0); samples(200, 4000); samples(480, 0); samples(200, 4000)
        samples(500, 0); samples(200, 4000); samples(1000, 0)
        assertEquals(RecordingEvidence(listOf(QuietGap(1880, 2380)), false, false), a.finish())
        val quiet = PcmAnalyzer(16_000).apply { repeat(500) { add(0) } }.finish()
        assertTrue(quiet.lowLevel)
        assertTrue(quiet.gaps.isEmpty())
        assertTrue(PcmAnalyzer(16_000).apply { add(Short.MIN_VALUE) }.finish().clipping)
    }

    @Test
    fun retryUsesOnlyAMatchingScenarioAndFocusAndChecksEvidenceAgainstTheNewTake() {
        val original = "Yesterday um we shipped."
        val metrics = SpeechAnalyzer.analyze(original, 10_000)
        val first = Attempt("first", 1, Scenario.UPDATE, CoachingGoal.FILLERS, 10_000, original,
            metrics, null, "Try the transition again.", null, null, null)
        val next = "Today we tested."
        val current = SpeechAnalyzer.analyze(next, 10_000)
        val prompt = CoachingHarness.prompt(next, current, CoachingGoal.FILLERS, PracticeContext(Scenario.UPDATE, first))
        assertContains(prompt, first.exercise)
        assertContains(prompt, "25.0 → 0.0")
        assertFalse(prompt.contains(original))
        assertFalse(CoachingHarness.prompt(next, current, CoachingGoal.PACE, PracticeContext(Scenario.UPDATE, first)).contains(first.exercise))
        assertFalse(CoachingHarness.prompt(next, current, CoachingGoal.FILLERS, PracticeContext(Scenario.EXPLAIN, first)).contains(first.exercise))
        kotlin.test.assertFailsWith<IllegalStateException> {
            CoachingHarness.verifiedAdvice("<suggestion><source>Yesterday</source><advice>Try a pause.</advice></suggestion>", next)
        }
    }
}
