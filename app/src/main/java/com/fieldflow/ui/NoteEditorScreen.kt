package com.fieldflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fieldflow.data.TranscriptSegmentEntity
import com.fieldflow.transform.TransformTemplate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    noteTitle: String,
    initialRawText: String,
    initialStructuredMarkdown: String,
    searchQuery: String,
    selectedFolder: String = "A_Story_Notes",
    availableFolders: List<String> = listOf("A_Story_Notes", "B_Code_Ideas", "C_Field_Logs", "D_Personal"),
    segments: List<TranscriptSegmentEntity> = emptyList(),
    isAudioPlaying: Boolean = false,
    isRecording: Boolean,
    liveRecordedText: String,
    onSelectFolder: (String) -> Unit = {},
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSaveRawText: (String) -> Unit,
    onSaveNamedNote: (fileName: String, content: String) -> Unit,
    onCopyText: (String) -> Unit,
    onApplyTransform: (TransformTemplate, String) -> Unit,
    onPrintNote: (String) -> Unit,
    onSharePdf: (String) -> Unit,
    onSeekAndPlayAudio: (startMs: Long) -> Unit = {},
    onStopAudioPlayback: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var rawText by remember(initialRawText) { mutableStateOf(initialRawText) }
    var structuredText by remember(initialStructuredMarkdown) { mutableStateOf(initialStructuredMarkdown) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var fileNameToSave by remember { mutableStateOf("") }
    var pendingTextToSave by remember { mutableStateOf("") }

    // If a recording was active and just produced live text, update the rawText box
    if (isRecording && liveRecordedText.isNotBlank()) {
        rawText = liveRecordedText
    }

    fun promptSave(content: String, defaultNamePrefix: String = "VoiceNote") {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val snippet = content.trim().take(20).replace(Regex("[^a-zA-Z0-9]"), "_").trim('_')
        val suggestedName = if (snippet.isNotBlank()) "${defaultNamePrefix}_$snippet" else "${defaultNamePrefix}_$stamp"
        fileNameToSave = suggestedName
        pendingTextToSave = content
        showSaveDialog = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // --- 0. Folder Slot Chips ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableFolders.forEach { folder ->
                val label = when (folder) {
                    "A_Story_Notes" -> "A: Story"
                    "B_Code_Ideas" -> "B: Code"
                    "C_Field_Logs" -> "C: Field"
                    "D_Personal" -> "D: Personal"
                    else -> folder
                }
                FilterChip(
                    selected = folder == selectedFolder,
                    onClick = { onSelectFolder(folder) },
                    label = { Text(label) }
                )
            }
        }

        // --- 1. Audio Recording Header Card ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isRecording) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recording English Speech...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Audio to Text (English)",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    if (isRecording) {
                        Button(
                            onClick = {
                                onStopRecording()
                                promptSave(rawText)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop & Save")
                        }
                    } else {
                        Button(
                            onClick = onStartRecording,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Record Audio")
                        }
                    }
                }

                if (isRecording && liveRecordedText.isNotBlank()) {
                    Text(
                        text = "Live: $liveRecordedText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // --- 2. Search Field ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search voice notes (FTS4)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = noteTitle.ifBlank { "Untitled Note" },
                style = MaterialTheme.typography.titleMedium
            )

            TextButton(
                onClick = { promptSave(if (selectedTab == 0) rawText else structuredText, "Note") }
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save As .txt")
            }
        }

        // --- 2.5. Audio Playback & Segments ---
        if (segments.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Tap-to-Seek Segments:",
                    style = MaterialTheme.typography.labelMedium
                )
                if (isAudioPlaying) {
                    TextButton(onClick = onStopAudioPlayback) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop Audio")
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                segments.forEach { seg ->
                    val sec = seg.startMs / 1000
                    val timeLabel = "%02d:%02d".format(sec / 60, sec % 60)
                    FilterChip(
                        selected = false,
                        onClick = { onSeekAndPlayAudio(seg.startMs) },
                        leadingIcon = {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        label = { Text(timeLabel) }
                    )
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Text Editor (.txt)") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Structured (.md)") }
            )
        }

        if (selectedTab == 0) {
            OutlinedTextField(
                value = rawText,
                onValueChange = {
                    rawText = it
                    onSaveRawText(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                placeholder = { Text("Audio transcription will appear here, or tap 'Record Audio' above to speak in English...") }
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = structuredText.ifBlank { "Tap a template below to transform your note with Gemini Nano." },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TransformTemplate.entries.forEach { template ->
                FilterChip(
                    selected = false,
                    onClick = { onApplyTransform(template, rawText) },
                    label = { Text(template.label) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onCopyText(if (selectedTab == 0) rawText else structuredText) },
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Copy")
            }
            OutlinedButton(
                onClick = { onPrintNote(if (selectedTab == 0) rawText else structuredText) },
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Print")
            }
            Button(
                onClick = { onSharePdf(if (selectedTab == 0) rawText else structuredText) },
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Text("Share PDF")
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }

    // --- Save File Dialog (Asks for filename) ---
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Transcript to Text File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a filename for your text file (.txt):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = fileNameToSave,
                        onValueChange = { fileNameToSave = it },
                        label = { Text("Filename") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Preview: ${pendingTextToSave.take(80).ifBlank { "(empty transcript)" }}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val validName = fileNameToSave.trim().ifBlank {
                            "VoiceNote_${System.currentTimeMillis()}"
                        }
                        onSaveNamedNote(validName, pendingTextToSave)
                        showSaveDialog = false
                    }
                ) {
                    Text("Save (.txt)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

