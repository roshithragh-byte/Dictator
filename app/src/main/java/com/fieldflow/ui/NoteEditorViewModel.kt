package com.fieldflow.ui

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldflow.data.FieldFlowDao
import com.fieldflow.data.NoteRepository
import com.fieldflow.data.RecordingEntity
import com.fieldflow.data.TranscriptSegmentEntity
import com.fieldflow.export.DocumentExporter
import com.fieldflow.transform.DocumentTransformer
import com.fieldflow.transform.TransformTemplate
import com.fieldflow.watcher.ClipboardHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class NoteEditorUiState(
    val selectedFolder: String = "A_Story_Notes",
    val availableFolders: List<String> = listOf("A_Story_Notes", "B_Code_Ideas", "C_Field_Logs", "D_Personal"),
    val recordings: List<RecordingEntity> = emptyList(),
    val activeRecordingId: String = "",
    val activeRecording: RecordingEntity? = null,
    val segments: List<TranscriptSegmentEntity> = emptyList(),
    val noteTitle: String = "",
    val rawText: String = "",
    val structuredMarkdown: String = "",
    val searchQuery: String = "",
    val isTransforming: Boolean = false,
    val isAudioPlaying: Boolean = false,
    val currentAudioPlaybackMs: Long = 0L,
    val statusMessage: String? = null
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val dao: FieldFlowDao,
    private val transformer: DocumentTransformer,
    private val exporter: DocumentExporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    init {
        loadRecordingsForFolder(_uiState.value.selectedFolder)
    }

    fun selectFolder(folderId: String) {
        _uiState.update { it.copy(selectedFolder = folderId) }
        loadRecordingsForFolder(folderId)
    }

    fun loadRecordingsForFolder(folderId: String) {
        viewModelScope.launch {
            dao.observeRecordingsByFolder(folderId).collect { recs ->
                _uiState.update { current ->
                    val active = if (current.activeRecordingId.isNotBlank()) {
                        recs.find { it.id == current.activeRecordingId } ?: recs.firstOrNull()
                    } else {
                        recs.firstOrNull()
                    }
                    val text = active?.let { runCatching { File(it.txtPath).readText() }.getOrDefault("") } ?: current.rawText
                    current.copy(
                        recordings = recs,
                        activeRecordingId = active?.id ?: "",
                        activeRecording = active,
                        noteTitle = active?.title ?: current.noteTitle,
                        rawText = if (current.rawText.isBlank() || current.activeRecordingId != (active?.id ?: "")) text else current.rawText
                    )
                }
                _uiState.value.activeRecordingId.takeIf { it.isNotBlank() }?.let { loadSegments(it) }
            }
        }
    }

    fun selectRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { File(recording.txtPath).readText() }.getOrDefault("")
            }
            val docs = withContext(Dispatchers.IO) {
                dao.getStructuredDocsForRecording(recording.id)
            }
            _uiState.update {
                it.copy(
                    activeRecordingId = recording.id,
                    activeRecording = recording,
                    noteTitle = recording.title,
                    rawText = text,
                    structuredMarkdown = docs.firstOrNull()?.markdownContent ?: ""
                )
            }
            loadSegments(recording.id)
        }
    }

    private fun loadSegments(recordingId: String) {
        viewModelScope.launch {
            val segments = withContext(Dispatchers.IO) {
                dao.getSegmentsForRecording(recordingId)
            }
            _uiState.update { it.copy(segments = segments) }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        viewModelScope.launch {
            if (query.isNotBlank()) {
                val hits = withContext(Dispatchers.IO) { repository.searchFts(query) }
                hits.firstOrNull()?.let { hit ->
                    val rec = withContext(Dispatchers.IO) { dao.getRecordingById(hit.id) }
                    if (rec != null) {
                        selectRecording(rec)
                    } else {
                        _uiState.update {
                            it.copy(
                                activeRecordingId = hit.id,
                                noteTitle = hit.title,
                                rawText = hit.text
                            )
                        }
                    }
                }
            } else {
                loadRecordingsForFolder(_uiState.value.selectedFolder)
            }
        }
    }

    fun updateRawText(newText: String) {
        _uiState.update { it.copy(rawText = newText) }
    }

    fun saveNamedNote(fileName: String, content: String, audioPath: String = "") {
        viewModelScope.launch {
            val savedFile = withContext(Dispatchers.IO) {
                repository.saveNamedNote(
                    fileName = fileName,
                    content = content,
                    targetDir = context.filesDir,
                    audioPath = audioPath,
                    folderId = _uiState.value.selectedFolder
                )
            }
            _uiState.update {
                it.copy(
                    noteTitle = savedFile.nameWithoutExtension,
                    rawText = content,
                    statusMessage = "Saved as ${savedFile.name}"
                )
            }
        }
    }

    fun applyTransform(template: TransformTemplate) {
        val currentText = _uiState.value.rawText
        if (currentText.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTransforming = true) }
            val result = withContext(Dispatchers.IO) {
                transformer.transform(currentText, template)
            }
            _uiState.update { it.copy(isTransforming = false) }
            result.onSuccess { transformed ->
                _uiState.update { it.copy(structuredMarkdown = transformed) }
                val recId = _uiState.value.activeRecordingId
                if (recId.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        repository.saveStructuredMarkdown(
                            recordingId = recId,
                            templateType = template.name,
                            markdownContent = transformed
                        )
                    }
                }
            }
        }
    }

    fun playAudioFromSegment(startMs: Long) {
        val audioPath = _uiState.value.activeRecording?.audioPath
        if (audioPath.isNullOrBlank() || !File(audioPath).exists()) {
            Toast.makeText(context, "No audio recording file for this note", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                seekTo(startMs.toInt())
                start()
                setOnCompletionListener {
                    _uiState.update { it.copy(isAudioPlaying = false) }
                }
            }
            _uiState.update {
                it.copy(isAudioPlaying = true, currentAudioPlaybackMs = startMs)
            }
        }.onFailure {
            Toast.makeText(context, "Unable to play audio: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopAudioPlayback() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
        _uiState.update { it.copy(isAudioPlaying = false) }
    }

    fun copyToClipboard(text: String) {
        viewModelScope.launch {
            ClipboardHelper.copyText(context, _uiState.value.noteTitle.ifBlank { "Dictator Note" }, text)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    fun printNote(activity: Activity, text: String) {
        exporter.printDocument(
            activity = activity,
            title = _uiState.value.noteTitle.ifBlank { "Dictator Note" },
            markdownContent = text
        )
    }

    fun exportAndSharePdf(context: Context, text: String) {
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                exporter.exportPdf(
                    context = context,
                    title = _uiState.value.noteTitle.ifBlank { "Dictator_Note" },
                    markdownContent = text
                )
            }
            exporter.shareFile(context, uri, "application/pdf")
        }
    }

    fun openPlaceInGoogleMaps(placeQuery: String) {
        exporter.openPlaceInGoogleMaps(context, placeQuery)
    }

    override fun onCleared() {
        super.onCleared()
        stopAudioPlayback()
    }
}
