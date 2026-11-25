package com.fieldflow.data

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "folder_slots")
data class FolderSlotEntity(
    @PrimaryKey val id: String,
    val slotCode: String, // "A".."D"
    val name: String,
    val defaultTemplate: String
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val folderId: String,
    val title: String,
    val audioPath: String,
    val txtPath: String,
    val durationMs: Long,
    val createdAtEpochMs: Long,
    val status: String // RECORDING, QUEUED, TRANSCRIBED, ERROR
)

@Entity(tableName = "transcript_segments")
data class TranscriptSegmentEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val segmentIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val rawText: String
)

@Entity(tableName = "structured_docs")
data class StructuredDocEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val templateType: String,
    val markdownContent: String,
    val mdPath: String,
    val updatedAtEpochMs: Long
)

@Fts4
@Entity(tableName = "recording_fts")
data class RecordingFtsEntity(
    val recordingId: String,
    val title: String,
    val rawText: String,
    val structuredText: String
)
