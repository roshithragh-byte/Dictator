package com.fieldflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldFlowDao {

    @Query("SELECT * FROM recordings ORDER BY createdAtEpochMs DESC")
    fun observeAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE folderId = :folderId ORDER BY createdAtEpochMs DESC")
    fun observeRecordingsByFolder(folderId: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun getLatestRecording(): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE id = :recordingId LIMIT 1")
    suspend fun getRecordingById(recordingId: String): RecordingEntity?

    @Query("SELECT * FROM transcript_segments WHERE recordingId = :recordingId ORDER BY segmentIndex ASC")
    suspend fun getSegmentsForRecording(recordingId: String): List<TranscriptSegmentEntity>

    @Query("SELECT * FROM structured_docs WHERE recordingId = :recordingId ORDER BY updatedAtEpochMs DESC")
    suspend fun getStructuredDocsForRecording(recordingId: String): List<StructuredDocEntity>

    @Query(
        """
        SELECT r.* FROM recordings r
        INNER JOIN recording_fts f ON r.id = f.recordingId
        WHERE recording_fts MATCH :ftsQuery
        ORDER BY r.createdAtEpochMs DESC
        """
    )
    suspend fun searchRecordingsFts(ftsQuery: String): List<RecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecording(recording: RecordingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStructuredDoc(doc: StructuredDocEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFts(fts: RecordingFtsEntity)
}
