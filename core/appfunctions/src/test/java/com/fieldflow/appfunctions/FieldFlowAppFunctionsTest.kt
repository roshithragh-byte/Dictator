package com.fieldflow.appfunctions

import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.fieldflow.data.FieldFlowDao
import com.fieldflow.data.NoteRepository
import com.fieldflow.data.RecordingEntity
import com.fieldflow.data.RecordingFtsEntity
import com.fieldflow.data.StructuredDocEntity
import com.fieldflow.data.TranscriptSegmentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.lang.reflect.Field

class FieldFlowAppFunctionsTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setupClass() {
            runCatching {
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply {
                    isAccessible = true
                }
                val unsafe = theUnsafeField.get(null)
                val staticFieldBaseMethod = unsafeClass.getMethod("staticFieldBase", Field::class.java)
                val staticFieldOffsetMethod = unsafeClass.getMethod("staticFieldOffset", Field::class.java)
                val putObjectMethod = unsafeClass.getMethod(
                    "putObject",
                    Any::class.java,
                    java.lang.Long.TYPE,
                    Any::class.java
                )

                val emptyField = android.os.Bundle::class.java.getDeclaredField("EMPTY")
                val base = staticFieldBaseMethod.invoke(unsafe, emptyField)
                val offset = staticFieldOffsetMethod.invoke(unsafe, emptyField) as Long
                putObjectMethod.invoke(unsafe, base, offset, android.os.Bundle())
            }
        }
    }

    private class FakeDao(
        var recordings: MutableList<RecordingEntity> = mutableListOf()
    ) : FieldFlowDao {
        override fun observeAllRecordings(): Flow<List<RecordingEntity>> = flowOf(recordings)
        override fun observeRecordingsByFolder(folderId: String): Flow<List<RecordingEntity>> = flowOf(recordings.filter { it.folderId == folderId })
        override suspend fun getLatestRecording(): RecordingEntity? = recordings.maxByOrNull { it.createdAtEpochMs }
        override suspend fun getRecordingById(recordingId: String): RecordingEntity? = recordings.find { it.id == recordingId }
        override suspend fun getSegmentsForRecording(recordingId: String): List<TranscriptSegmentEntity> = emptyList()
        override suspend fun getStructuredDocsForRecording(recordingId: String): List<StructuredDocEntity> = emptyList()
        override suspend fun searchRecordingsFts(ftsQuery: String): List<RecordingEntity> {
            val q = ftsQuery.removeSuffix("*").lowercase()
            return recordings.filter { it.title.lowercase().contains(q) }
        }
        override suspend fun upsertRecording(recording: RecordingEntity) {
            recordings.removeAll { it.id == recording.id }
            recordings.add(recording)
        }
        override suspend fun insertSegments(segments: List<TranscriptSegmentEntity>) {}
        override suspend fun upsertStructuredDoc(doc: StructuredDocEntity) {}
        override suspend fun upsertFts(fts: RecordingFtsEntity) {}
    }

    @Test
    fun testBlankQueryThrowsAppFunctionInvalidArgumentException() = runBlocking {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val ops = FieldFlowAppFunctionsOps(repo)

        try {
            ops.searchVoiceNotes("   ")
            fail("Expected AppFunctionInvalidArgumentException")
        } catch (e: AppFunctionInvalidArgumentException) {
            assertEquals("Search query must not be blank. Provide a keyword or phrase.", e.message)
        }
    }

    @Test
    fun testEmptyDatabaseThrowsAppFunctionElementNotFoundException() = runBlocking {
        val dao = FakeDao()
        val repo = NoteRepository(dao)
        val ops = FieldFlowAppFunctionsOps(repo)

        try {
            ops.getLatestNote()
            fail("Expected AppFunctionElementNotFoundException")
        } catch (e: AppFunctionElementNotFoundException) {
            assertEquals(
                "No voice notes found. Press and hold the FieldFlow floating bubble to record one.",
                e.message
            )
        }
    }

    @Test
    fun testSuccessfulSearchAndGetLatest() = runBlocking {
        val tempFile = File.createTempFile("note_test", ".txt").apply {
            writeText("Sprint planning meeting with architecture notes.")
            deleteOnExit()
        }
        val dao = FakeDao(
            mutableListOf(
                RecordingEntity(
                    id = "rec_123",
                    folderId = "A_Story_Notes",
                    title = "Architecture Plan",
                    audioPath = "",
                    txtPath = tempFile.absolutePath,
                    durationMs = 12000L,
                    createdAtEpochMs = 1700000000000L,
                    status = "TRANSCRIBED"
                )
            )
        )
        val repo = NoteRepository(dao)
        val ops = FieldFlowAppFunctionsOps(repo)

        val latest = ops.getLatestNote()
        assertEquals("rec_123", latest.id)
        assertEquals("Architecture Plan", latest.title)
        assertEquals("A_Story_Notes", latest.folder)
        assertEquals("Sprint planning meeting with architecture notes.", latest.text)

        val hits = ops.searchVoiceNotes("Architecture")
        assertEquals(1, hits.size)
        assertEquals("Architecture Plan", hits[0].title)
    }
}
