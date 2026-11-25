package com.fieldflow.data

import java.io.File
import java.time.Instant

data class NoteSearchHit(
    val id: String,
    val title: String,
    val folderId: String,
    val text: String,
    val createdAtIso: String
)

class NoteRepository(private val dao: FieldFlowDao) {

    suspend fun searchFts(query: String): List<NoteSearchHit> {
        val sanitized = query.trim().replace("\"", "") + "*"
        return dao.searchRecordingsFts(sanitized).map { rec ->
            val raw = runCatching { File(rec.txtPath).readText() }.getOrDefault("")
            NoteSearchHit(
                id = rec.id,
                title = rec.title,
                folderId = rec.folderId,
                text = raw,
                createdAtIso = Instant.ofEpochMilli(rec.createdAtEpochMs).toString()
            )
        }
    }

    suspend fun getLatestNote(): NoteSearchHit? {
        val rec = dao.getLatestRecording() ?: return null
        val raw = runCatching { File(rec.txtPath).readText() }.getOrDefault("")
        return NoteSearchHit(
            id = rec.id,
            title = rec.title,
            folderId = rec.folderId,
            text = raw,
            createdAtIso = Instant.ofEpochMilli(rec.createdAtEpochMs).toString()
        )
    }

    suspend fun saveNamedNote(
        fileName: String,
        content: String,
        targetDir: File,
        audioPath: String = "",
        folderId: String = "A_Voice_Notes"
    ): File {
        val cleanName = if (fileName.endsWith(".txt", ignoreCase = true)) fileName else "$fileName.txt"
        val folder = File(targetDir, "recordings/$folderId").apply { mkdirs() }
        val txtFile = File(folder, cleanName)
        txtFile.writeText(content)

        val id = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.upsertRecording(
            RecordingEntity(
                id = id,
                folderId = folderId,
                title = cleanName.removeSuffix(".txt"),
                audioPath = audioPath,
                txtPath = txtFile.absolutePath,
                durationMs = 0L,
                createdAtEpochMs = now,
                status = "TRANSCRIBED"
            )
        )
        dao.upsertFts(
            RecordingFtsEntity(
                recordingId = id,
                title = cleanName.removeSuffix(".txt"),
                rawText = content,
                structuredText = ""
            )
        )
        return txtFile
    }

    suspend fun saveStructuredMarkdown(
        recordingId: String,
        templateType: String,
        markdownContent: String
    ): File? {
        val rec = dao.getRecordingById(recordingId) ?: return null
        val txtFile = File(rec.txtPath)
        val mdFile = File(txtFile.parentFile, "${txtFile.nameWithoutExtension}_${templateType.lowercase()}.md")
        mdFile.writeText(markdownContent)

        val now = System.currentTimeMillis()
        dao.upsertStructuredDoc(
            StructuredDocEntity(
                id = "${recordingId}_$templateType",
                recordingId = recordingId,
                templateType = templateType,
                markdownContent = markdownContent,
                mdPath = mdFile.absolutePath,
                updatedAtEpochMs = now
            )
        )
        val raw = runCatching { txtFile.readText() }.getOrDefault("")
        dao.upsertFts(
            RecordingFtsEntity(
                recordingId = recordingId,
                title = rec.title,
                rawText = raw,
                structuredText = markdownContent
            )
        )
        return mdFile
    }
}
