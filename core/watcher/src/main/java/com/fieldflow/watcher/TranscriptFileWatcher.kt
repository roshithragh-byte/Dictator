package com.fieldflow.watcher

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface TranscriptFileWatcher {
    suspend fun appendSegment(
        folderId: String,
        sessionStem: String,
        timestampRange: String,
        text: String,
        mirrorToPublicDocuments: Boolean = false
    ): Uri
}

/**
 * S2-3: Atomic .txt file watcher.
 * Canonical transcripts live in app-private filesDir/recordings/<folderId>/<sessionStem>.txt
 * (protected by Android FBE). Optionally mirrors to public Documents/FieldFlow/ via MediaStore.
 */
class AtomicTranscriptFileWatcher(
    private val context: Context
) : TranscriptFileWatcher {

    override suspend fun appendSegment(
        folderId: String,
        sessionStem: String,
        timestampRange: String,
        text: String,
        mirrorToPublicDocuments: Boolean
    ): Uri = withContext(Dispatchers.IO) {
        val folderDir = File(context.filesDir, "recordings/$folderId").apply { mkdirs() }
        val targetFile = File(folderDir, "$sessionStem.txt")
        val tmpFile = File(folderDir, "$sessionStem.txt.tmp")

        val existing = if (targetFile.exists()) targetFile.readText() else ""
        val line = "[$timestampRange] ${text.trim()}\n"
        tmpFile.writeText(existing + line)
        if (!tmpFile.renameTo(targetFile)) {
            targetFile.writeText(existing + line)
            tmpFile.delete()
        }

        if (mirrorToPublicDocuments) {
            mirrorToMediaStore(folderId, "$sessionStem.txt", targetFile.readText())
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", targetFile)
    }

    private fun mirrorToMediaStore(folderId: String, fileName: String, fullText: String) {
        val resolver = context.contentResolver
        val relPath = "${Environment.DIRECTORY_DOCUMENTS}/FieldFlow/$folderId"
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val existingUri = resolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            arrayOf(fileName, "$relPath/"),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
            } else null
        }

        val uri = existingUri ?: resolver.insert(
            MediaStore.Files.getContentUri("external"),
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            }
        ) ?: return

        resolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(fullText.toByteArray(Charsets.UTF_8))
        }
    }
}
