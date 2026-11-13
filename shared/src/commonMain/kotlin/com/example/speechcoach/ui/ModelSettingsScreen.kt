package com.example.speechcoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ModelSettingsActions(
    val back: () -> Unit,
    val downloadSpeech: () -> Unit,
    val browseSpeech: () -> Unit,
    val downloadCoach: () -> Unit,
    val browseCoach: () -> Unit,
    val dismissError: () -> Unit,
)

@Composable
fun ModelSettingsScreen(
    state: PracticeState,
    actions: ModelSettingsActions,
    speechFileName: String,
    coachFileName: String,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = actions.back) { Text("Back to practice") }
            Text("Model settings", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Download a model from Hugging Face or choose an existing file from your device. " +
                    "Only the supported model files below can be imported."
            )
            if (state.status != null || state.error != null || state.progress != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OperationStatus(state, actions.dismissError)
                    }
                }
            }
            ModelSettingsCard(
                title = "Speech recognition",
                description = "Parakeet-TDT-0.6B-v3 · 615 MB",
                fileName = speechFileName,
                ready = state.speechModelReady,
                enabled = !state.busy && !state.recording,
                download = actions.downloadSpeech,
                browse = actions.browseSpeech,
            )
            ModelSettingsCard(
                title = "Gemma 4 coaching (optional)",
                description = "Gemma 4 E2B · 2.59 GB · requires a capable 64-bit device",
                fileName = coachFileName,
                ready = state.coachModelReady,
                enabled = !state.busy && !state.recording,
                download = actions.downloadCoach,
                browse = actions.browseCoach,
            )
            if (state.recording) Text("Stop recording before managing models.")
            Text(
                "Files are copied to app-private storage and verified using SHA-256. " +
                    "Keep enough free space for the copy and model runtime.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Speech model: NVIDIA Parakeet-TDT-0.6B-v3, quantized by LiteRT Community. " +
                    "CC BY 4.0: creativecommons.org/licenses/by/4.0/; model: " +
                    "huggingface.co/litert-community/parakeet-tdt-0.6b-v3",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ModelSettingsCard(
    title: String,
    description: String,
    fileName: String,
    ready: Boolean,
    enabled: Boolean,
    download: () -> Unit,
    browse: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description)
            Text(if (ready) "Installed" else "Not installed", style = MaterialTheme.typography.labelLarge)
            Text("Supported file: $fileName", style = MaterialTheme.typography.bodySmall)
            if (!ready) {
                OutlinedButton(onClick = download, enabled = enabled) { Text("Download") }
            }
            OutlinedButton(onClick = browse, enabled = enabled) {
                Text(if (ready) "Replace from local file" else "Browse local files")
            }
        }
    }
}
