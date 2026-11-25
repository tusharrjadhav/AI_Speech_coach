package com.example.speechcoach.core

interface SpeechOutput {
    /** Speaks until completion; cancellation stops speech and releases resources. */
    suspend fun speak(text: String)

    /** Interrupts current speech promptly; cancel its coroutine before starting other audio. */
    fun stop()
}
