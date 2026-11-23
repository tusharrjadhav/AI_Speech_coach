package com.example.speechcoach

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.speechcoach.ui.PracticeActions
import com.example.speechcoach.ui.PracticeScreen
import com.example.speechcoach.ui.ModelSettingsActions
import com.example.speechcoach.ui.ModelSettingsScreen
import com.example.speechcoach.ui.theme.SpeechCoachTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PracticeViewModel by viewModels {
        viewModelFactory {
            initializer { PracticeViewModel(application, AndroidSpeechOutput(application)) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            var showRationale by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            var showTranscriptionConsent by remember { mutableStateOf(false) }
            var showingModelSettings by rememberSaveable { mutableStateOf(false) }
            val speechPicker = rememberLauncherForActivityResult(LocalModelDocument()) { uri ->
                if (uri != null) viewModel.importModel(LocalModel.SPEECH, uri)
            }
            val coachPicker = rememberLauncherForActivityResult(LocalModelDocument()) { uri ->
                if (uri != null) viewModel.importModel(LocalModel.COACH, uri)
            }
            BackHandler(enabled = showingModelSettings) { showingModelSettings = false }
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    viewModel.record()
                } else if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    showSettings = true
                } else {
                    showRationale = true
                }
            }
            SpeechCoachTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(Modifier.safeDrawingPadding()) {
                        if (showingModelSettings) {
                            ModelSettingsScreen(
                                state = state,
                                actions = ModelSettingsActions(
                                    back = { showingModelSettings = false },
                                    downloadSpeech = { viewModel.download(LocalModel.SPEECH) },
                                    browseSpeech = { speechPicker.launch(arrayOf("*/*")) },
                                    downloadCoach = { viewModel.download(LocalModel.COACH) },
                                    browseCoach = { coachPicker.launch(arrayOf("*/*")) },
                                    dismissError = viewModel::dismissError,
                                ),
                                speechFileName = LocalModel.SPEECH.fileName,
                                coachFileName = LocalModel.COACH.fileName,
                            )
                        } else {
                            PracticeScreen(
                                state,
                                PracticeActions(
                                    record = {
                                        when {
                                            ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) ==
                                                PackageManager.PERMISSION_GRANTED -> viewModel.record()
                                            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> showRationale = true
                                            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                    stop = viewModel::stopRecording,
                                    play = viewModel::play,
                                    delete = viewModel::deleteRecording,
                                    openSettings = {
                                        viewModel.stopSuggestionReading()
                                        showingModelSettings = true
                                    },
                                    transcribe = { showTranscriptionConsent = true },
                                    editTranscript = viewModel::editTranscript,
                                    selectCoachingGoal = viewModel::selectCoachingGoal,
                                    coach = viewModel::coach,
                                    improve = viewModel::improve,
                                    readSuggestion = viewModel::readSuggestion,
                                    stopSuggestion = viewModel::stopSuggestionReading,
                                    dismissError = viewModel::dismissError,
                                ),
                            )
                        }
                    }
                }
                if (showRationale) {
                    AlertDialog(
                        onDismissRequest = { showRationale = false },
                        title = { Text("Microphone access") },
                        text = { Text("Speech Coach needs the microphone to record your practice locally.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showRationale = false
                                permission.launch(Manifest.permission.RECORD_AUDIO)
                            }) { Text("Continue") }
                        },
                        dismissButton = { TextButton(onClick = { showRationale = false }) { Text("Not now") } },
                    )
                }
                if (showSettings) {
                    AlertDialog(
                        onDismissRequest = { showSettings = false },
                        title = { Text("Microphone access is off") },
                        text = { Text("Enable microphone access in Settings to record a practice.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showSettings = false
                                startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        .setData(Uri.fromParts("package", packageName, null))
                                )
                            }) { Text("Open Settings") }
                        },
                        dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Not now") } },
                    )
                }
                if (showTranscriptionConsent) {
                    AlertDialog(
                        onDismissRequest = { showTranscriptionConsent = false },
                        title = { Text("MediaPipe privacy") },
                        text = {
                            Text(
                                "Audio is processed on this device. Google says MediaPipe sends API " +
                                    "performance and usage metrics to Google. Learn more at " +
                                    "developers.google.com/edge/mediapipe/solutions/privacy."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showTranscriptionConsent = false
                                viewModel.transcribe()
                            }) { Text("Agree and transcribe") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showTranscriptionConsent = false }) { Text("Cancel") }
                        },
                    )
                }
            }
        }
    }

    override fun onStop() {
        viewModel.stopRecording()
        viewModel.stopSuggestionReading()
        super.onStop()
    }
}

private class LocalModelDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
}
