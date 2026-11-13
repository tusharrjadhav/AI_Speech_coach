package com.example.speechcoach.transcription

import com.example.speechcoach.core.Transcript
import com.example.speechcoach.core.TranscriptSegment

internal class TranscriptAssembler {
    private val segments = mutableListOf<TranscriptSegment>()
    private var interim: String? = null

    fun accept(text: String, startMillis: Long, endMillis: Long, isFinal: Boolean) {
        if (!isFinal) {
            interim = text
            return
        }
        val previous = segments.lastOrNull()
        val cleaned = if (previous != null && previous.endMillis == startMillis &&
            previous.endMillis > previous.startMillis && endMillis > startMillis
        ) {
            removeConfirmedOverlap(previous.text, text, interim)
        } else {
            text.trim()
        }
        if (cleaned.isNotEmpty()) segments.add(TranscriptSegment(cleaned, startMillis, endMillis))
        interim = null
    }

    fun result(): Transcript = Transcript(segments.joinToString(" ") { it.text }, segments.toList())

    private fun removeConfirmedOverlap(previous: String, current: String, hypothesis: String?): String {
        if (hypothesis.isNullOrBlank()) return current.trim()
        val previousWords = words.findAll(previous).map { it.value.lowercase() }.toList()
        val currentMatches = words.findAll(current).toList()
        val currentWords = currentMatches.map { it.value.lowercase() }
        val interimWords = words.findAll(hypothesis).map { it.value.lowercase() }.toList()
        if (interimWords.size < 2 || currentWords.take(2) == interimWords.take(2)) return current.trim()

        // A prior interim must independently confirm the continuation after the overlapping words.
        val maxOverlap = minOf(previousWords.size, currentWords.size - 2)
        for (overlap in maxOverlap downTo 1) {
            if (previousWords.takeLast(overlap) == currentWords.take(overlap) &&
                currentWords.drop(overlap).take(2) == interimWords.take(2)
            ) {
                return current.substring(currentMatches[overlap].range.first).trim()
            }
        }
        return current.trim()
    }

    private companion object {
        val words = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
    }
}
