package com.example.speechcoach

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.example.speechcoach.core.SpeechOutput
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AndroidSpeechOutput(context: Context) : SpeechOutput {
    private val context = context.applicationContext
    private val lock = Any()
    private var activeEngine: TextToSpeech? = null

    override suspend fun speak(text: String) = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "There are no suggestions to read." }
        val job = currentCoroutineContext().job
        val initialized = CompletableDeferred<Int>()
        val engine = TextToSpeech(context) { initialized.complete(it) }
        try {
            synchronized(lock) {
                job.ensureActive()
                activeEngine = engine
            }
            val status = withTimeoutOrNull(15_000) { initialized.await() }
                ?: throw IOException("Text-to-speech did not start. Check your device's text-to-speech settings.")
            if (status != TextToSpeech.SUCCESS) {
                throw IOException("Text-to-speech is unavailable. Check your device's text-to-speech settings.")
            }
            job.ensureActive()
            val voice = selectOfflineVoice(engine.voices.orEmpty(), Locale.getDefault(), engine.defaultVoice)
                ?: throw IOException(
                    "No installed offline voice is available for this device language. " +
                        "Download one in your device's text-to-speech settings."
                )
            if (engine.setVoice(voice) != TextToSpeech.SUCCESS) {
                throw IOException("The offline voice could not be selected. Check your text-to-speech settings.")
            }
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            if (engine.setAudioAttributes(attributes) != TextToSpeech.SUCCESS) {
                throw IOException("Could not configure text-to-speech audio.")
            }
            val audioManager = context.getSystemService(AudioManager::class.java)
                ?: throw IOException("Audio is unavailable on this device.")
            val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener({ change ->
                    if (change < 0) {
                        job.cancel(CancellationException("Speech audio focus was lost."))
                        synchronized(lock) {
                            if (activeEngine === engine) engine.stop()
                        }
                    }
                }, Handler(Looper.getMainLooper()))
                .build()
            job.ensureActive()
            if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                throw IOException("Could not start speech audio. Return to the app and try again.")
            }
            try {
                for (chunk in speechChunks(text, TextToSpeech.getMaxSpeechInputLength() - 1)) {
                    if (chunk.isNotBlank()) speakChunk(engine, chunk)
                }
            } finally {
                audioManager.abandonAudioFocusRequest(focus)
            }
        } finally {
            synchronized(lock) {
                if (activeEngine === engine) activeEngine = null
                engine.stop()
                engine.shutdown()
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            activeEngine?.stop()
        }
    }

    private suspend fun speakChunk(engine: TextToSpeech, text: String) {
        val job = currentCoroutineContext().job
        val utteranceId = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Unit>()
        val listener = object : UtteranceProgressListener() {
            override fun onStart(id: String) = Unit
            override fun onDone(id: String) {
                if (id == utteranceId) completion.complete(Unit)
            }

            @Deprecated("Deprecated in Android")
            override fun onError(id: String) {
                if (id == utteranceId) completion.completeExceptionally(IOException("Text-to-speech could not read the suggestions."))
            }

            override fun onError(id: String, errorCode: Int) {
                if (id == utteranceId) {
                    completion.completeExceptionally(IOException("Text-to-speech failed (error $errorCode). Check your offline voice."))
                }
            }

            override fun onStop(id: String, interrupted: Boolean) {
                if (id == utteranceId) completion.completeExceptionally(IOException("Reading was interrupted."))
            }
        }
        synchronized(lock) {
            job.ensureActive()
            if (engine.setOnUtteranceProgressListener(listener) != TextToSpeech.SUCCESS ||
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS
            ) {
                throw IOException("Text-to-speech could not start reading the suggestions.")
            }
        }
        val completed = withTimeoutOrNull(60_000L + text.length * 1_000L) {
            completion.await()
            true
        } ?: false
        if (!completed) {
            throw IOException("Text-to-speech stopped responding. Check your device's text-to-speech settings.")
        }
    }
}

fun selectOfflineVoice(voices: Collection<Voice>, locale: Locale, defaultVoice: Voice?): Voice? {
    val available = voices.filter {
        !it.isNetworkConnectionRequired &&
            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features &&
            it.locale.language == locale.language
    }
    return available.firstOrNull { it == defaultVoice }
        ?: available.firstOrNull { it.locale == locale }
        ?: available.firstOrNull { it.locale.country == locale.country }
        ?: available.firstOrNull()
}

internal fun speechChunks(text: String, maxLength: Int): List<String> {
    require(maxLength >= 2)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + maxLength, text.length)
        if (end < text.length) {
            if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            val whitespace = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
            if (whitespace >= start + maxLength / 2) end = whitespace + 1
        }
        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}
