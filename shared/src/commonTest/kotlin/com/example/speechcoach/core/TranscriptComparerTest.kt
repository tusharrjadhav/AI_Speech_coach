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
    fun groupsReplacementWordsAndKeepsSeparateEdits() {
        val comparison = TranscriptComparer.compare(
            "We finished the launch on time and shared the report.",
            "We finished the launch early and sent the report.",
        )
        val edits = TranscriptComparer.suggestedEdits(comparison)
        assertEquals(2, edits.size)
        assertEquals("on time", edits[0].before?.trim())
        assertEquals("early", edits[0].after?.trim())
        assertEquals("shared", edits[1].before?.trim())
        assertEquals("sent", edits[1].after?.trim())
    }

    @Test
    fun distinguishesInsertedAndRemovedText() {
        val insertion = TranscriptComparer.suggestedEdits(TranscriptComparer.compare("Hello!", "Hello there!"))
        assertEquals(null, insertion.single().before)
        assertEquals("there", insertion.single().after?.trim())
        val removal = TranscriptComparer.suggestedEdits(TranscriptComparer.compare("Hello there!", "Hello!"))
        assertEquals("there", removal.single().before?.trim())
        assertEquals(null, removal.single().after)
        assertTrue(TranscriptComparer.suggestedEdits(TranscriptComparer.compare("Same.", "Same.")).isEmpty())
    }

    @Test
    fun keepsWhitespaceOnlyEditsDistinguishable() {
        val edit = TranscriptComparer.suggestedEdits(
            TranscriptComparer.compare("Hello  world", "Hello\tworld")
        ).single()
        assertEquals("  ", edit.before)
        assertEquals("\t", edit.after)
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
