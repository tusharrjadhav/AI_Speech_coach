package com.example.speechcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.speechcoach.core.DeliveryAnalysis
import com.example.speechcoach.core.PracticeLoop
import com.example.speechcoach.core.RecordingEvidence
import com.example.speechcoach.core.SpeechAnalyzer

@Composable
internal fun TranscriptMetrics(metrics: DeliveryAnalysis, transcript: String) {
    Text("Words: ${metrics.wordCount}")
    Text("Estimated pace: ${metrics.wordsPerMinute?.let { "$it words per minute" } ?: "unavailable"}")
    Text("Possible “um” / “uh”: ${metrics.possibleFillers.values.sum()}" +
        (metrics.fillersPer100?.let { " ($it per 100 words)" } ?: ""))
    metrics.repeatedPhrases.forEach { (phrase, count) ->
        Text("Repeated pair: “$phrase” ($count times)")
        remember(transcript, phrase) { SpeechAnalyzer.phraseExamples(transcript, phrase) }.forEach { excerpt ->
            Text("“$excerpt”", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (metrics.sections.isNotEmpty()) {
        Text("Approximate recognition sections (not word timings)")
        metrics.sections.forEach { Text("${it.startMillis / 1000}–${it.endMillis / 1000} s: ${it.wordsPerMinute} WPM") }
    }
}

@Composable
internal fun AudioMetrics(audio: RecordingEvidence?, replayFrom: ((Long) -> Unit)?) {
    if (audio == null) return
    Text("Recording checks", style = MaterialTheme.typography.titleSmall)
    if (audio.clipping) Text("Possible clipping. Try moving farther from the microphone.")
    if (audio.lowLevel) Text("Low recording level. Check the microphone position.")
    Text("Quiet gaps of 0.5 s or longer between louder sounds: ${audio.gaps.size}. Noise and mic level affect detection.")
    audio.gaps.sortedByDescending { it.endMillis - it.startMillis }.take(3).forEach {
        val label = "Quiet gap: ${it.startMillis / 1000.0}–${it.endMillis / 1000.0} s"
        if (replayFrom == null) Text(label)
        else TextButton(onClick = { replayFrom((it.startMillis - 500).coerceAtLeast(0)) }) { Text("Replay · $label") }
    }
}

@Composable
fun HistoryScreen(
    state: PracticeState, back: () -> Unit, open: (String) -> Unit,
    retry: () -> Unit, play: () -> Unit, replayFrom: (Long) -> Unit, delete: () -> Unit, dismissError: () -> Unit,
    formatDate: (Long) -> String,
) {
    val attempt = state.selectedAttempt
    var confirm by remember(attempt?.id) { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TextButton(onClick = back, enabled = !state.busy) { Text(if (attempt == null) "Back to practice" else "All attempts") }
            Text("Local history", style = MaterialTheme.typography.headlineMedium)
            OperationStatus(state, dismissError)
        }
        if (attempt == null) {
            if (state.history.isEmpty()) item { Text("No saved attempts. Record a take, review the transcript and choose Save attempt.") }
            items(state.history, key = { it.id }) { entry ->
                OutlinedButton(onClick = { open(entry.id) }, enabled = !state.busy) {
                    Text("${entry.scenario.title} · ${formatDate(entry.savedAt)}")
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(attempt.scenario.title, style = MaterialTheme.typography.titleLarge)
                    Text("${formatDate(attempt.savedAt)} · ${attempt.goal.label} · ${attempt.durationMillis / 1000.0} s")
                    Text(attempt.scenario.prompt)
                    Button(onClick = retry, enabled = !state.busy) { Text("Practice this again") }
                    OutlinedButton(onClick = play, enabled = !state.busy) {
                        Text(if (state.playing) "Stop playback" else "Play saved recording")
                    }
                    Text("Reviewed transcript", style = MaterialTheme.typography.titleMedium)
                    Text(attempt.transcript)
                    TranscriptMetrics(attempt.metrics, attempt.transcript)
                    AudioMetrics(attempt.audio, if (state.busy) null else replayFrom)
                    state.selectedBaselineMetrics?.let {
                        Text("Compared with your saved take", style = MaterialTheme.typography.titleMedium)
                        Text(PracticeLoop.comparison(it, attempt.metrics))
                    }
                    Text("Next-take exercise", style = MaterialTheme.typography.titleMedium)
                    Text(attempt.exercise)
                    attempt.advice?.let { Text("Saved AI suggestions"); Text(it) }
                    attempt.revision?.let { Text("Suggested rewrite (not the original)"); Text(it) }
                    TextButton(onClick = { confirm = true }, enabled = !state.busy) { Text("Delete saved attempt") }
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete saved attempt?") },
        text = { Text("Remove its saved recording, text and feedback from history. This does not delete the current practice draft.") },
        confirmButton = { TextButton(onClick = { confirm = false; delete() }) { Text("Delete from history") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}
