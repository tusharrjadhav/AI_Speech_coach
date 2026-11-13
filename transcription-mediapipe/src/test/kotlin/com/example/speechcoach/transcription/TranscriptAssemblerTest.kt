package com.example.speechcoach.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptAssemblerTest {
    @Test
    fun removesBoundaryOverlapsConfirmedByPreviousInterimResults() {
        val assembler = TranscriptAssembler()
        assembler.accept("Today I want to practice speaking clearly, the team", 0, 1250, true)
        assembler.accept("completed the project on time, we learned several", 1250, 6500, false)
        assembler.accept("team completed the project", 1250, 2500, true)
        assembler.accept("on time. We learned several useful lessons along", 2500, 7500, false)
        assembler.accept("project on time. We learned", 2500, 3750, true)
        assembler.accept("several useful lessons along the way, I really", 3750, 9000, false)
        assembler.accept("we learned several useful lessons", 3750, 5000, true)
        assembler.accept("along the way. I really really enjoyed working", 5000, 10000, false)
        assembler.accept("useful lessons along the way.", 5000, 6250, true)
        assembler.accept("I really really enjoyed working with everyone.", 6250, 11095, true)

        assertEquals(
            "Today I want to practice speaking clearly, the team completed the project on time. " +
                "We learned several useful lessons along the way. I really really enjoyed working with everyone.",
            assembler.result().text,
        )
    }

    @Test
    fun preservesRepetitionsWhenInterimIncludesThem() {
        val assembler = TranscriptAssembler()
        assembler.accept("I really", 0, 1000, true)
        assembler.accept("really enjoyed working together", 1000, 2000, false)
        assembler.accept("really enjoyed working together", 1000, 2000, true)

        assertEquals("I really really enjoyed working together", assembler.result().text)
    }

    @Test
    fun removesAnExtraBoundaryWordWithoutRemovingRealRepeatedWords() {
        val assembler = TranscriptAssembler()
        assembler.accept("We really really", 0, 1000, true)
        assembler.accept("need to practice", 1000, 2000, false)
        assembler.accept("really need to practice", 1000, 2000, true)

        assertEquals("We really really need to practice", assembler.result().text)
    }

    @Test
    fun keepsAmbiguousOrNonAdjacentRepetitions() {
        val assembler = TranscriptAssembler()
        assembler.accept("A project", 0, 1000, true)
        assembler.accept("project starts today", 1000, 2000, true)
        assembler.accept("starts today", 2100, 2500, false)
        assembler.accept("today starts today", 2100, 3000, true)

        assertEquals("A project project starts today today starts today", assembler.result().text)
    }

    @Test
    fun matchesCaseAndPunctuationWithoutChangingRemainingText() {
        val assembler = TranscriptAssembler()
        assembler.accept("We completed the Project.", 0, 1000, true)
        assembler.accept("on time, which matters", 1000, 2000, false)
        assembler.accept("project, on time!", 1000, 2000, true)

        assertEquals("We completed the Project. on time!", assembler.result().text)
        assertEquals(listOf(0L, 1000L), assembler.result().segments.map { it.startMillis })
    }
}
