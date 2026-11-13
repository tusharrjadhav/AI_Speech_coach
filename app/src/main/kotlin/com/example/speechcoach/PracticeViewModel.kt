package com.example.speechcoach

import android.app.Application
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speechcoach.coaching.GemmaCoach
import com.example.speechcoach.core.AudioClip
import com.example.speechcoach.core.SpeechAnalyzer
import com.example.speechcoach.transcription.MediaPipeTranscriber
import com.example.speechcoach.ui.PracticeState
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val recorder = AudioRecorder(application)
    private val models = ModelStore(application)
    private val preferences = application.getSharedPreferences("practice", 0)
    private var clip: AudioClip? = preferences.getString("recording", null)?.let { path ->
        File(path).takeIf { it.isFile && it.length() > 44 }?.let {
            AudioClip(it.absolutePath, (it.length() - 44) * 1000 / (AudioRecorder.SAMPLE_RATE * 2))
        }
    }
    private val _state = MutableStateFlow(
        PracticeState(
            hasRecording = clip != null,
            durationSeconds = (clip?.durationMillis ?: 0) / 1000,
            speechModelReady = models.isReady(LocalModel.SPEECH),
            coachModelReady = models.isReady(LocalModel.COACH),
        )
    )
    val state = _state.asStateFlow()
    private var recordJob: Job? = null
    private var player: MediaPlayer? = null

    fun record() {
        if (state.value.busy || state.value.recording || recordJob?.isActive == true) return
        stopPlayback()
        recorder.start()
        _state.update { it.copy(recording = true, status = "Recording…", error = null) }
        recordJob = viewModelScope.launch {
            try {
                val next = recorder.record()
                val old = clip
                val saved = withContext(Dispatchers.IO) {
                    preferences.edit().putString("recording", next.path).commit()
                }
                if (!saved) throw IOException("Could not save the recording reference.")
                clip = next
                val oldFile = old?.let { File(it.path) }
                val oldDeleted = oldFile == null || !oldFile.exists() ||
                    oldFile.path == next.path || oldFile.delete()
                _state.update {
                    it.copy(
                        recording = false,
                        hasRecording = true,
                        durationSeconds = next.durationMillis / 1000,
                        transcript = "",
                        analysis = null,
                        suggestion = null,
                        status = "Recording saved on this device.",
                    )
                }
                if (!oldDeleted) {
                    reportError("Previous recording was not deleted", IOException("It remains in app-private storage."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportError("Recording failed", e)
                _state.update { it.copy(recording = false, status = null) }
            }
        }
    }

    fun stopRecording() {
        if (state.value.recording) {
            recorder.stop()
            _state.update { it.copy(status = "Finishing recording…") }
        }
    }

    fun play() {
        val current = clip ?: return
        if (player != null) {
            stopPlayback()
            return
        }
        try {
            val newPlayer = MediaPlayer()
            player = newPlayer
            newPlayer.setDataSource(current.path)
            newPlayer.setOnPreparedListener {
                it.start()
                _state.update { state -> state.copy(playing = true) }
            }
            newPlayer.setOnCompletionListener { stopPlayback() }
            newPlayer.setOnErrorListener { _, what, extra ->
                stopPlayback()
                reportError("Playback failed", IOException("MediaPlayer error $what ($extra)."))
                true
            }
            newPlayer.prepareAsync()
            _state.update { it.copy(playing = true, error = null) }
        } catch (e: Exception) {
            stopPlayback()
            reportError("Playback failed", e)
        }
    }

    fun deleteRecording() {
        val current = clip ?: return
        stopPlayback()
        val file = File(current.path)
        if (file.exists() && !file.delete()) {
            reportError("Delete failed", IOException("Could not delete the recording."))
            return
        }
        preferences.edit().remove("recording").apply()
        clip = null
        _state.update {
            it.copy(
                hasRecording = false, durationSeconds = 0, transcript = "",
                analysis = null, suggestion = null, status = "Recording deleted.", error = null,
            )
        }
    }

    fun download(model: LocalModel) {
        if (model == LocalModel.COACH && !android.os.Process.is64Bit()) {
            reportError("Gemma 4 is unavailable", IllegalStateException("A 64-bit device is required."))
            return
        }
        runOperation("Downloading ${model.label}…") {
            models.download(model) { complete, total ->
                _state.update {
                    it.copy(
                        progress = complete.toFloat() / total,
                        status = if (complete == total) "Verifying ${model.label}…" else it.status,
                    )
                }
            }
            _state.update {
                it.copy(
                    speechModelReady = models.isReady(LocalModel.SPEECH),
                    coachModelReady = models.isReady(LocalModel.COACH),
                )
            }
            "${model.label} model is ready."
        }
    }

    fun transcribe() {
        val current = clip ?: return
        runOperation("Transcribing on this device…") {
            val result = MediaPipeTranscriber(getApplication(), models.file(LocalModel.SPEECH)).transcribe(current)
            check(result.text.isNotBlank()) { "No speech was recognized. Try again or enter the transcript manually." }
            _state.update {
                it.copy(
                    transcript = result.text,
                    analysis = SpeechAnalyzer.analyze(result.text, current.durationMillis),
                    suggestion = null,
                )
            }
            "Transcript ready. Review and correct it before using the estimates."
        }
    }

    fun editTranscript(text: String) {
        _state.update {
            it.copy(
                transcript = text,
                analysis = clip?.let { current -> SpeechAnalyzer.analyze(text, current.durationMillis) },
                suggestion = null,
            )
        }
    }

    fun coach() {
        val snapshot = state.value
        val report = snapshot.analysis ?: return
        runOperation("Generating local suggestions; this may take a while…") {
            val suggestion = GemmaCoach(models.file(LocalModel.COACH), getApplication<Application>().cacheDir)
                .suggest(snapshot.transcript, report)
            _state.update { it.copy(suggestion = suggestion) }
            "Suggestions are ready."
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun runOperation(label: String, operation: suspend () -> String) {
        if (state.value.busy || state.value.recording) return
        stopPlayback()
        _state.update { it.copy(busy = true, status = label, progress = null, error = null) }
        viewModelScope.launch {
            try {
                val message = operation()
                _state.update { it.copy(status = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LinkageError) {
                reportError("This device cannot load the selected local model runtime", e)
                _state.update { it.copy(status = null) }
            } catch (e: Exception) {
                reportError(label.removeSuffix("…"), e)
                _state.update { it.copy(status = null) }
            } finally {
                _state.update { it.copy(busy = false, progress = null) }
            }
        }
    }

    private fun stopPlayback() {
        player?.release()
        player = null
        _state.update { it.copy(playing = false) }
    }

    private fun reportError(action: String, exception: Throwable) {
        Log.e("SpeechCoach", action, exception)
        _state.update { it.copy(error = "$action: ${exception.message ?: exception.javaClass.simpleName}") }
    }

    override fun onCleared() {
        recorder.stop()
        stopPlayback()
        super.onCleared()
    }
}
