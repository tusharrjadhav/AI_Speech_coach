package com.example.speechcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechAnalyzerTest {
    @Test
    fun calculatesPaceAndCaseInsensitiveWholeWordFillers() {
        val report = SpeechAnalyzer.analyze("Um, I think, uh, the drum is nice.", 30_000)
        assertEquals(8, report.wordCount)
        assertEquals(16, report.wordsPerMinute)
        assertEquals(mapOf("um" to 1, "uh" to 1), report.possibleFillers)
    }

    @Test
    fun emptyTranscriptAndInvalidDurationHaveNoPace() {
        assertNull(SpeechAnalyzer.analyze("", 12_000).wordsPerMinute)
        assertNull(SpeechAnalyzer.analyze("hello", 0).wordsPerMinute)
    }

    @Test
    fun fillerMatchingDoesNotCountPartsOfWords() {
        val report = SpeechAnalyzer.analyze("umbrella um uh-huh like", 60_000)
        assertEquals(mapOf("um" to 1, "uh" to 1), report.possibleFillers)
    }
}
