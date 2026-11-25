package com.example.speechcoach.core

import kotlinx.serialization.Serializable

enum class Scenario(val title: String, val prompt: String) {
    UPDATE("Team update", "Tell your team what changed, why it matters, and what comes next."),
    INTERVIEW("Interview", "Describe a challenge, what you did, and the result."),
    EXPLAIN("Explain an idea", "Explain one idea to someone new to it. Give one concrete example."),
}

@Serializable
data class Attempt(
    val id: String,
    val savedAt: Long,
    val scenario: Scenario,
    val goal: CoachingGoal,
    val durationMillis: Long,
    val transcript: String,
    val metrics: DeliveryAnalysis,
    val audio: RecordingEvidence?,
    val exercise: String,
    val advice: String?,
    val revision: String?,
    val baselineId: String?,
)

data class AttemptLabel(val id: String, val savedAt: Long, val scenario: Scenario)

interface PracticeHistory : AutoCloseable {
    suspend fun list(): List<AttemptLabel>
    suspend fun get(id: String): Attempt?
    suspend fun save(attempt: Attempt, clip: AudioClip)
    suspend fun readAudio(id: String): ByteArray
    suspend fun delete(id: String)
}

data class PracticeContext(val scenario: Scenario = Scenario.UPDATE, val previous: Attempt? = null) {
    fun baselineFor(goal: CoachingGoal): Attempt? = previous?.takeIf { it.scenario == scenario && it.goal == goal }
}

object PracticeLoop {
    fun exercise(goal: CoachingGoal, metrics: DeliveryAnalysis): String = when {
        goal == CoachingGoal.FILLERS && metrics.possibleFillers.isNotEmpty() ->
            "Find one possible filler in your transcript. Say that sentence again with a quiet beat instead."
        goal == CoachingGoal.CLARITY && metrics.repeatedPhrases.isNotEmpty() ->
            "Read the passages around “${metrics.repeatedPhrases.keys.first()}”. On the next take, keep the repetition only if it helps."
        else -> goal.practiceTip
    }

    fun comparison(before: DeliveryAnalysis, after: DeliveryAnalysis): String = buildList {
        add("Words: ${before.wordCount} → ${after.wordCount}")
        if (before.wordsPerMinute != null && after.wordsPerMinute != null)
            add("Estimated WPM: ${before.wordsPerMinute} → ${after.wordsPerMinute}")
        if (before.fillersPer100 != null && after.fillersPer100 != null)
            add("Possible fillers / 100 words: ${before.fillersPer100} → ${after.fillersPer100}")
    }.joinToString(" · ")
}
