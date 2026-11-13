package com.example.speechcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.speechcoach.core.DeliveryAnalysis
import com.example.speechcoach.core.TranscriptChange
import com.example.speechcoach.core.TranscriptComparison

data class PracticeState(
    val recording: Boolean = false,
    val hasRecording: Boolean = false,
    val durationSeconds: Long = 0,
    val playing: Boolean = false,
    val transcript: String = "",
    val analysis: DeliveryAnalysis? = null,
    val speechModelReady: Boolean = false,
    val coachModelReady: Boolean = false,
    val busy: Boolean = false,
    val status: String? = null,
    val progress: Float? = null,
    val error: String? = null,
    val suggestion: String? = null,
    val improvement: TranscriptComparison? = null,
)

data class PracticeActions(
    val record: () -> Unit,
    val stop: () -> Unit,
    val play: () -> Unit,
    val delete: () -> Unit,
    val openSettings: () -> Unit,
    val transcribe: () -> Unit,
    val editTranscript: (String) -> Unit,
    val coach: () -> Unit,
    val improve: () -> Unit,
    val dismissError: () -> Unit,
)

@Composable
fun PracticeScreen(state: PracticeState, actions: PracticeActions) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Header(actions.openSettings)
                    RecordingCard(state, actions)
                    ModelCard(state, actions)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    ResultsCard(state, actions)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Header(actions.openSettings)
                RecordingCard(state, actions)
                ModelCard(state, actions)
                ResultsCard(state, actions)
            }
        }
    }
}

@Composable
private fun Header(openSettings: () -> Unit) {
    Text("Speech Coach", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Practice privately, review your words, and try again.",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    TextButton(onClick = openSettings) { Text("Model settings") }
}

@Composable
private fun RecordingCard(state: PracticeState, actions: PracticeActions) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("1. Record a practice", style = MaterialTheme.typography.titleLarge)
            Text("Record up to 2 minutes. Audio stays on this device and recording stops when the app leaves the screen.")
            if (state.recording) {
                Button(onClick = actions.stop) { Text("Stop recording") }
            } else {
                Button(onClick = actions.record, enabled = !state.busy) { Text("Start recording") }
            }
            if (state.hasRecording) {
                Text(
                    if (state.durationSeconds == 0L) "Recording: less than 1 second"
                    else "Recording: ${state.durationSeconds} seconds"
                )
                OutlinedButton(onClick = actions.play, enabled = !state.recording && !state.busy) {
                    Text(if (state.playing) "Stop playback" else "Play")
                }
                OutlinedButton(onClick = actions.delete, enabled = !state.recording && !state.busy) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ModelCard(state: PracticeState, actions: PracticeActions) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("2. On-device models", style = MaterialTheme.typography.titleLarge)
            Text(if (state.speechModelReady) "Speech model ready" else "Speech model not installed")
            Text(if (state.coachModelReady) "Gemma 4 ready" else "Optional Gemma 4 not installed")
            OutlinedButton(onClick = actions.openSettings) {
                Text("Manage models")
            }
            OperationStatus(state, actions.dismissError)
        }
    }
}

@Composable
internal fun OperationStatus(state: PracticeState, dismissError: () -> Unit) {
    state.status?.let { Text(it) }
    state.progress?.let { progress ->
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text("${(progress * 100).toInt()}%")
    }
    state.error?.let { error ->
        Text(error, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = dismissError) { Text("Dismiss error") }
    }
}

@Composable
private fun ResultsCard(state: PracticeState, actions: PracticeActions) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("3. Review and improve", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = actions.transcribe,
                enabled = state.hasRecording && state.speechModelReady && !state.recording && !state.busy,
            ) { Text("Transcribe on device") }
            OutlinedTextField(
                value = state.transcript,
                onValueChange = actions.editTranscript,
                label = { Text("Transcript (editable)") },
                supportingText = { Text("Correct recognition errors before reviewing the estimates.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                enabled = !state.busy && !state.recording,
            )
            state.analysis?.let { analysis ->
                Text("Words: ${analysis.wordCount}")
                Text("Estimated pace: ${analysis.wordsPerMinute?.let { "$it words per minute" } ?: "unavailable"}")
                Text("Possible “um” / “uh”: ${analysis.possibleFillers.values.sum()}")
                Text(
                    "These simple word estimates are best suited to English. Pace includes the whole recording, including silence. " +
                        "Speech recognition can miss or add fillers; zero does not prove there were none.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = actions.coach,
                enabled = state.coachModelReady && state.analysis != null &&
                    state.transcript.isNotBlank() && !state.busy && !state.recording,
            ) { Text("Get optional local suggestions") }
            if (state.busy) {
                state.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            state.suggestion?.let {
                Text("AI suggestions (experimental)", style = MaterialTheme.typography.titleMedium)
                Text(it)
                Text("Check suggestions: the model cannot hear your delivery.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = actions.improve,
                    enabled = !state.busy && !state.recording,
                ) { Text("Create improved transcript") }
            }
            state.improvement?.let { TranscriptComparisonView(it) }
            Spacer(Modifier.padding(2.dp))
        }
    }
}

@Composable
private fun TranscriptComparisonView(comparison: TranscriptComparison) {
    var view by remember(comparison) { mutableStateOf(ComparisonView.CHANGES) }
    Text("Compare transcripts", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = view == ComparisonView.CHANGES,
            onClick = { view = ComparisonView.CHANGES },
            label = { Text("Changes") },
        )
        FilterChip(
            selected = view == ComparisonView.ORIGINAL,
            onClick = { view = ComparisonView.ORIGINAL },
            label = { Text("Original") },
        )
        FilterChip(
            selected = view == ComparisonView.IMPROVED,
            onClick = { view = ComparisonView.IMPROVED },
            label = { Text("Improved") },
        )
    }
    val removedColor = MaterialTheme.colorScheme.error
    val addedColor = MaterialTheme.colorScheme.primary
    val spans = when (view) {
        ComparisonView.CHANGES -> comparison.changes
        ComparisonView.ORIGINAL -> comparison.original
        ComparisonView.IMPROVED -> comparison.improved
    }
    val text = remember(spans, removedColor, addedColor, view) {
        buildAnnotatedString {
            spans.forEachIndexed { index, span ->
                val previous = spans.getOrNull(index - 1)
                if (
                    view == ComparisonView.CHANGES && previous != null &&
                    previous.changed && span.changed && previous.change != span.change &&
                    !previous.text.last().isWhitespace() && !span.text.first().isWhitespace()
                ) {
                    append(" ")
                }
                when (span.change) {
                    TranscriptChange.REMOVED ->
                        withStyle(SpanStyle(color = removedColor, textDecoration = TextDecoration.LineThrough)) {
                            append(span.text)
                        }
                    TranscriptChange.ADDED ->
                        withStyle(SpanStyle(color = addedColor, textDecoration = TextDecoration.Underline)) {
                            append(span.text)
                        }
                    TranscriptChange.UNCHANGED -> append(span.text)
                }
            }
        }
    }
    if (view == ComparisonView.CHANGES) {
        Text(text, modifier = Modifier.testTag("transcript-comparison-text"))
    } else {
        SelectionContainer {
            Text(text, modifier = Modifier.testTag("transcript-comparison-text"))
        }
    }
    Text(
        "Struck-through text was removed; underlined text was added. Check changes carefully: " +
            "Gemma can remove important details. The original above is never replaced.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (comparison.approximate) {
        Text(
            "For this long comparison, highlighting is approximate.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private enum class ComparisonView { CHANGES, ORIGINAL, IMPROVED }
