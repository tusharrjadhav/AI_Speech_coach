package com.example.speechcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.speechcoach.core.DeliveryAnalysis
import com.example.speechcoach.core.SuggestedEdit
import com.example.speechcoach.core.TranscriptChange
import com.example.speechcoach.core.TranscriptComparer
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
            LocalSuggestions(state, actions)
            Spacer(Modifier.padding(2.dp))
        }
    }
}

@Composable
private fun LocalSuggestions(state: PracticeState, actions: PracticeActions) {
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text("Local coaching", style = MaterialTheme.typography.titleMedium)
    Text(
        "Get wording suggestions from Gemma. It reads your transcript, not the recording.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!state.coachModelReady) {
        Text("Install Gemma in Model settings to use local coaching.", style = MaterialTheme.typography.bodySmall)
    }
    OutlinedButton(
        onClick = actions.coach,
        enabled = state.coachModelReady && state.analysis != null &&
            state.transcript.isNotBlank() && !state.busy && !state.recording,
    ) { Text("Get local suggestions") }
    if (state.busy) {
        state.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    state.suggestion?.let { suggestion ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI suggestions", style = MaterialTheme.typography.titleSmall)
                Text(remember(suggestion) { formatSuggestion(suggestion) })
                Text(
                    "Experimental: check the advice before using it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text(
            "Create an improved version to highlight suggested word changes.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = actions.improve,
            enabled = !state.busy && !state.recording,
        ) { Text("Create improved transcript") }
    }
    state.improvement?.let { TranscriptComparisonView(it) }
}

private fun formatSuggestion(text: String): AnnotatedString = buildAnnotatedString {
    var start = 0
    while (start < text.length) {
        val open = text.indexOf("**", start)
        if (open < 0) {
            append(text.substring(start))
            break
        }
        val close = text.indexOf("**", open + 2)
        if (close < 0 || close == open + 2) {
            append(text.substring(start))
            break
        }
        append(text.substring(start, open))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(open + 2, close)) }
        start = close + 2
    }
}

@Composable
private fun TranscriptComparisonView(comparison: TranscriptComparison) {
    var view by remember(comparison) { mutableStateOf(ComparisonView.CHANGES) }
    var visibleEdits by remember(comparison) { mutableStateOf(3) }
    val edits = remember(comparison) {
        if (comparison.approximate) emptyList() else TranscriptComparer.suggestedEdits(comparison)
    }
    Text("Compare transcripts", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
    if (view == ComparisonView.CHANGES && !comparison.approximate) {
        if (edits.isEmpty()) {
            Text("No wording changes suggested.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Suggested changes", style = MaterialTheme.typography.titleSmall)
            edits.take(visibleEdits).forEach { SuggestedEditCard(it) }
            if (visibleEdits < edits.size) {
                TextButton(onClick = { visibleEdits += 3 }) {
                    Text("Show more changes (${edits.size - visibleEdits} remaining)")
                }
            }
        }
    }
    val removedColor = MaterialTheme.colorScheme.onErrorContainer
    val removedBackground = MaterialTheme.colorScheme.errorContainer
    val addedColor = MaterialTheme.colorScheme.onSecondaryContainer
    val addedBackground = MaterialTheme.colorScheme.secondaryContainer
    val spans = when (view) {
        ComparisonView.CHANGES -> comparison.changes
        ComparisonView.ORIGINAL -> comparison.original
        ComparisonView.IMPROVED -> comparison.improved
    }
    val text = remember(spans, removedColor, removedBackground, addedColor, addedBackground, view) {
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
                        withStyle(
                            SpanStyle(
                                color = removedColor,
                                background = removedBackground,
                                textDecoration = TextDecoration.LineThrough,
                            )
                        ) {
                            append(span.text)
                        }
                    TranscriptChange.ADDED ->
                        withStyle(
                            SpanStyle(
                                color = addedColor,
                                background = addedBackground,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline,
                            )
                        ) {
                            append(span.text)
                        }
                    TranscriptChange.UNCHANGED -> append(span.text)
                }
            }
        }
    }
    if (view == ComparisonView.CHANGES) {
        val spoken = remember(spans) {
            spans.joinToString("") { span ->
                when (span.change) {
                    TranscriptChange.REMOVED -> " Removed: ${span.text}. "
                    TranscriptChange.ADDED -> " Added: ${span.text}. "
                    TranscriptChange.UNCHANGED -> span.text
                }
            }
        }
        Text(
            text,
            modifier = Modifier.testTag("transcript-comparison-text").clearAndSetSemantics {
                contentDescription = spoken
            },
        )
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

@Composable
private fun SuggestedEditCard(edit: SuggestedEdit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EditText(
                label = "Before",
                text = edit.before,
                change = TranscriptChange.REMOVED,
                modifier = Modifier.weight(1f),
            )
            EditText(
                label = "After",
                text = edit.after,
                change = TranscriptChange.ADDED,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EditText(label: String, text: String?, change: TranscriptChange, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (text == null) {
            Text("No text", style = MaterialTheme.typography.bodyMedium)
        } else {
            val isRemoved = change == TranscriptChange.REMOVED
            Surface(
                color = if (isRemoved) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isRemoved) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    displayEditText(text),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (isRemoved) TextDecoration.LineThrough else TextDecoration.Underline,
                    fontWeight = if (isRemoved) FontWeight.Normal else FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun displayEditText(text: String): String {
    if (text.isNotBlank()) return text.trim()
    return when {
        text.all { it == ' ' } -> if (text.length == 1) "Space" else "${text.length} spaces"
        text.all { it == '\n' } -> if (text.length == 1) "Line break" else "${text.length} line breaks"
        text.all { it == '\t' } -> if (text.length == 1) "Tab" else "${text.length} tabs"
        else -> "Whitespace"
    }
}

private enum class ComparisonView { CHANGES, ORIGINAL, IMPROVED }
