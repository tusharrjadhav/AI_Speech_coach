package com.example.speechcoach.core

import kotlin.math.roundToInt

data class AudioClip(val path: String, val durationMillis: Long)

data class TranscriptSegment(val text: String, val startMillis: Long, val endMillis: Long)

data class Transcript(val text: String, val segments: List<TranscriptSegment> = emptyList())

data class DeliveryAnalysis(
    val wordCount: Int,
    val wordsPerMinute: Int?,
    val possibleFillers: Map<String, Int>,
)

interface SpeechTranscriber {
    suspend fun transcribe(clip: AudioClip): Transcript
}

interface SpeechCoach {
    suspend fun suggest(transcript: String, analysis: DeliveryAnalysis): String
}

object SpeechAnalyzer {
    private val words = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
    private val fillerWords = setOf("um", "uh")

    fun analyze(transcript: String, durationMillis: Long): DeliveryAnalysis {
        val tokens = words.findAll(transcript).map { it.value.lowercase() }.toList()
        val fillers = tokens.filter { it in fillerWords }.groupingBy { it }.eachCount()
        val pace = if (durationMillis > 0 && tokens.isNotEmpty()) {
            (tokens.size * 60_000.0 / durationMillis).roundToInt()
        } else {
            null
        }
        return DeliveryAnalysis(tokens.size, pace, fillers)
    }
}
