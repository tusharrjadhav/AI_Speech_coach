package com.example.speechcoach.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranscriptComparerTest {
    @Test
    fun marksReplacementsWithoutChangingEitherTranscript() {
        val original = "We finished the launch on time."
        val improved = "We finished the launch early."
        val result = TranscriptComparer.compare(original, improved)

        assertEquals(original, result.original.joinToString("") { it.text })
        assertEquals(improved, result.improved.joinToString("") { it.text })
        assertEquals("on time", result.original.filter { it.changed }.joinToString("") { it.text }.trim())
        assertEquals("early", result.improved.filter { it.changed }.joinToString("") { it.text }.trim())
        assertEquals(original, result.changes.filter { it.change != TranscriptChange.ADDED }.joinToString("") { it.text })
        assertEquals(improved, result.changes.filter { it.change != TranscriptChange.REMOVED }.joinToString("") { it.text })
        assertFalse(result.approximate)
    }

    @Test
    fun showsRemovedTimingEvenWhenNoWordsWereAdded() {
        val result = TranscriptComparer.compare(
            "Our team completed the launch on time. Tomorrow we will review customer feedback.",
            "Our team completed the launch. Tomorrow we will review customer feedback.",
        )
        assertEquals(
            "on time",
            result.changes.filter { it.change == TranscriptChange.REMOVED }.joinToString("") { it.text }.trim(),
        )
        assertTrue(result.changes.none { it.change == TranscriptChange.ADDED })
    }

    @Test
    fun marksRepeatedWordsAndPunctuationSeparately() {
        val original = "I I can do this."
        val improved = "I can do this!"
        val result = TranscriptComparer.compare(original, improved)

        assertEquals(original, result.original.joinToString("") { it.text })
        assertEquals(improved, result.improved.joinToString("") { it.text })
        assertTrue(result.original.filter { it.changed }.joinToString("") { it.text }.contains("I"))
        assertTrue(result.original.filter { it.changed }.joinToString("") { it.text }.contains("."))
        assertEquals("!", result.improved.filter { it.changed }.joinToString("") { it.text })
    }

    @Test
    fun handlesIdenticalAndEmptyVersions() {
        val identical = TranscriptComparer.compare("Hello, world!", "Hello, world!")
        assertTrue(identical.original.none { it.changed })
        assertTrue(identical.improved.none { it.changed })
        val insertion = TranscriptComparer.compare("", "Hello!")
        assertTrue(insertion.original.isEmpty())
        assertEquals("Hello!", insertion.improved.joinToString("") { it.text })
        assertTrue(insertion.improved.all { it.changed })
    }

    @Test
    fun flagsCoarseHighlightingWhenTheComparisonWouldBeTooLarge() {
        val original = "a!".repeat(1500)
        val improved = "b?".repeat(1500)
        val result = TranscriptComparer.compare(original, improved)

        assertTrue(result.approximate)
        assertEquals(original, result.original.joinToString("") { it.text })
        assertEquals(improved, result.improved.joinToString("") { it.text })
    }
}
