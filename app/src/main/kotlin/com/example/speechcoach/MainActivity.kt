package com.example.speechcoach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.speechcoach.ui.PracticeActions
import com.example.speechcoach.ui.PracticeScreen
import com.example.speechcoach.ui.theme.SpeechCoachTheme

class MainActivity : ComponentActivity() {
    private val viewModel: PracticeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsState()
            var showRationale by remember { mutableStateOf(false) }
            var showSettings by remember { mutableStateOf(false) }
            var showTranscriptionConsent by remember { mutableStateOf(false) }
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
                                downloadSpeech = { viewModel.download(LocalModel.SPEECH) },
                                transcribe = { showTranscriptionConsent = true },
                                editTranscript = viewModel::editTranscript,
                                downloadCoach = { viewModel.download(LocalModel.COACH) },
                                coach = viewModel::coach,
                                dismissError = viewModel::dismissError,
                            ),
                        )
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
        super.onStop()
    }
}
