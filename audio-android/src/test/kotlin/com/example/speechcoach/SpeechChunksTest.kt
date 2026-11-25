package com.example.speechcoach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechChunksTest {
    @Test
    fun preservesLongTextWithinTheLimitWithoutBreakingSurrogatePairs() {
        val text = "one two three four \uD83D\uDE00five six seven"
        val chunks = speechChunks(text, 8)

        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 8 })
        assertTrue(chunks.none { it.last().isHighSurrogate() || it.first().isLowSurrogate() })
    }

    @Test
    fun splitsLongUnbrokenWords() {
        assertEquals(listOf("abcdefgh", "ijkl"), speechChunks("abcdefghijkl", 8))
    }

    @Test
    fun keepsSurrogatePairsTogetherAtTheBoundary() {
        assertEquals(listOf("abcdefg", "\uD83D\uDE00xyz"), speechChunks("abcdefg\uD83D\uDE00xyz", 8))
    }
}
