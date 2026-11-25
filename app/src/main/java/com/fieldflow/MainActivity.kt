package com.fieldflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.fieldflow.audio.AudioCaptureController
import com.fieldflow.audio.EnglishSpeechRecorder
import com.fieldflow.ui.NoteEditorScreen
import com.fieldflow.ui.NoteEditorViewModel
import com.fieldflow.ui.PermissionOnboardingScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: NoteEditorViewModel by viewModels()

    private val speechRecorder by lazy {
        EnglishSpeechRecorder(
            context = this,
            audioCaptureController = AudioCaptureController(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecorder.destroy()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen() {
        var hasPermissions by remember {
            mutableStateOf(checkAllPermissions())
        }
        var showSettingsScreen by remember { mutableStateOf(false) }

        val uiState by viewModel.uiState.collectAsState()

        val isRecording by speechRecorder.isRecording.collectAsState()
        val liveRecordedText by speechRecorder.liveText.collectAsState()

        LaunchedEffect(uiState.statusMessage) {
            uiState.statusMessage?.let {
                Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
            }
        }

        if (!hasPermissions || showSettingsScreen) {
            PermissionOnboardingScreen(
                onOverlayStarted = {
                    hasPermissions = checkAllPermissions()
                    showSettingsScreen = false
                    moveTaskToBack(true)
                }
            )
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Dictator") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        actions = {
                            IconButton(onClick = { viewModel.loadRecordingsForFolder(uiState.selectedFolder) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reload notes")
                            }
                            IconButton(onClick = { showSettingsScreen = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Overlay & Permissions")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    NoteEditorScreen(
                        noteTitle = uiState.noteTitle,
                        initialRawText = uiState.rawText,
                        initialStructuredMarkdown = uiState.structuredMarkdown,
                        searchQuery = uiState.searchQuery,
                        selectedFolder = uiState.selectedFolder,
                        availableFolders = uiState.availableFolders,
                        segments = uiState.segments,
                        isAudioPlaying = uiState.isAudioPlaying,
                        isRecording = isRecording,
                        liveRecordedText = liveRecordedText,
                        onSelectFolder = { folder ->
                            viewModel.selectFolder(folder)
                        },
                        onStartRecording = {
                            speechRecorder.start()
                        },
                        onStopRecording = {
                            val finalSpeech = speechRecorder.stop()
                            if (finalSpeech.isNotBlank()) {
                                viewModel.updateRawText(finalSpeech)
                            }
                        },
                        onSearchQueryChange = { q ->
                            viewModel.onSearchQueryChange(q)
                        },
                        onSaveRawText = { updated ->
                            viewModel.updateRawText(updated)
                        },
                        onSaveNamedNote = { fileName, content ->
                            viewModel.saveNamedNote(
                                fileName = fileName,
                                content = content,
                                audioPath = speechRecorder.lastRecordedAudioFile?.absolutePath ?: ""
                            )
                        },
                        onCopyText = { toCopy ->
                            viewModel.copyToClipboard(toCopy)
                        },
                        onApplyTransform = { template, _ ->
                            viewModel.applyTransform(template)
                        },
                        onPrintNote = { textToPrint ->
                            viewModel.printNote(this@MainActivity, textToPrint)
                        },
                        onSharePdf = { textToShare ->
                            viewModel.exportAndSharePdf(this@MainActivity, textToShare)
                        },
                        onSeekAndPlayAudio = { startMs ->
                            viewModel.playAudioFromSegment(startMs)
                        },
                        onStopAudioPlayback = {
                            viewModel.stopAudioPlayback()
                        }
                    )

                    if (uiState.isTransforming) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }

    private fun checkAllPermissions(): Boolean {
        val hasOverlay = Settings.canDrawOverlays(this)
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        return hasOverlay && hasMic
    }
}
