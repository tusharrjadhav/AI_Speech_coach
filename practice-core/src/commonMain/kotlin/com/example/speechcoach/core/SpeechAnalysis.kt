package com.example.speechcoach.core

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

data class AudioClip(val path: String, val durationMillis: Long)

data class TranscriptSegment(val text: String, val startMillis: Long, val endMillis: Long)

data class Transcript(val text: String, val segments: List<TranscriptSegment> = emptyList())

@Serializable
data class DeliveryAnalysis(
    val wordCount: Int,
    val wordsPerMinute: Int?,
    val possibleFillers: Map<String, Int>,
    val repeatedPhrases: Map<String, Int> = emptyMap(),
    val sections: List<PaceSection> = emptyList(),
) {
    val fillersPer100: Double? get() = if (wordCount == 0) null else
        (possibleFillers.values.sum() * 1000.0 / wordCount).roundToInt() / 10.0
}

@Serializable
data class PaceSection(val startMillis: Long, val endMillis: Long, val wordsPerMinute: Int)

enum class CoachingGoal(val label: String, val practiceTip: String, val instruction: String) {
    CLARITY(
        "Clarity and structure",
        "Practice expressing one idea at a time while keeping important details.",
        "Focus on wording and structure, not vocal delivery. Suggest a specific change to the " +
            "wording when it would make the excerpt clearer without removing or changing any facts. " +
            "Consider the opening, main point or conclusion when present. If the wording is already clear, " +
            "say so and suggest how to practice its structure.",
    ),
    PACE(
        "Pace and pauses",
        "Try a brief pause between ideas, and adapt your pace to your audience.",
        "Suggest where to practice a pause or how to vary pace, without claiming to have heard the recording.",
    ),
    FILLERS(
        "Filler words",
        "If you need a moment to think, try a pause. Occasional fillers are normal.",
        "Suggest a practical way to manage fillers without claiming any were spoken if they do not appear " +
            "in the transcript. Do not treat occasional fillers as a problem.",
    ),
}

interface SpeechTranscriber {
    suspend fun transcribe(clip: AudioClip): Transcript
}

interface SpeechCoach {
    suspend fun suggest(
        transcript: String,
        analysis: DeliveryAnalysis,
        goal: CoachingGoal = CoachingGoal.CLARITY,
        context: PracticeContext = PracticeContext(),
    ): String
}

interface TranscriptImprover {
    suspend fun improve(transcript: String, advice: String): String
}

object SpeechAnalyzer {
    private val words = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*")
    private val fillerWords = setOf("um", "uh")

    /** Up to two excerpts in the speaker's original casing and punctuation. */
    fun phraseExamples(transcript: String, phrase: String): List<String> {
        val tokens = words.findAll(transcript).toList()
        val pair = phrase.split(' ')
        if (pair.size != 2) return emptyList()
        return (0 until tokens.lastIndex).asSequence().filter { index ->
            tokens[index].value.equals(pair[0], ignoreCase = true) &&
                tokens[index + 1].value.equals(pair[1], ignoreCase = true)
        }.map { index ->
            val first = tokens[maxOf(0, index - 2)].range.first
            val last = tokens[minOf(tokens.lastIndex, index + 3)].range.last
            transcript.substring(first..last)
        }.distinct().take(2).toList()
    }

    fun analyze(transcript: String, durationMillis: Long, segments: List<TranscriptSegment> = emptyList()): DeliveryAnalysis {
        val tokens = words.findAll(transcript).map { it.value.lowercase() }.toList()
        val fillers = tokens.filter { it in fillerWords }.groupingBy { it }.eachCount()
        val pace = if (durationMillis > 0 && tokens.isNotEmpty()) {
            (tokens.size * 60_000.0 / durationMillis).roundToInt()
        } else {
            null
        }
        val repeats = tokens.zipWithNext { a, b -> "$a $b" }.groupingBy { it }.eachCount()
            .filterValues { it >= 2 }.entries.sortedByDescending { it.value }.take(3).associate { it.toPair() }
        // Decoder sections are approximate, not word alignments. Never apply their timing to edited text.
        val sections = if (segments.size >= 2 && segments.joinToString(" ") { it.text } == transcript &&
            segments.all { it.startMillis >= 0 && it.endMillis > it.startMillis && it.endMillis <= durationMillis } &&
            segments.zipWithNext().all { (a, b) -> a.endMillis <= b.startMillis }
        ) segments.mapNotNull {
            val length = it.endMillis - it.startMillis
            val count = words.findAll(it.text).count()
            if (length >= 3_000 && count >= 3) PaceSection(it.startMillis, it.endMillis, (60_000.0 * count / length).roundToInt())
            else null
        }.takeIf { it.size >= 2 }.orEmpty() else emptyList()
        return DeliveryAnalysis(tokens.size, pace, fillers, repeats, sections)
    }
}
