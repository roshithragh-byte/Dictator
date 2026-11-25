package com.fieldflow.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.fieldflow.data.FieldFlowDatabase
import com.fieldflow.data.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AppFunctionSerializable(isDescribedByKDoc = true)
data class VoiceNoteSummary(
    val id: String,
    val title: String,
    val folder: String,
    val text: String,
    val createdAtIso: String
)

/**
 * S3-3: AppFunctions logic operations class separated for clean testability.
 */
class FieldFlowAppFunctionsOps(private val repository: NoteRepository) {

    /**
     * Searches recorded FieldFlow voice notes and transcripts by keyword.
     * @param query Non-empty keyword, character name, part number, or phrase to search for.
     * @throws AppFunctionInvalidArgumentException if query is blank.
     */
    suspend fun searchVoiceNotes(
        query: String
    ): List<VoiceNoteSummary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            throw AppFunctionInvalidArgumentException(
                "Search query must not be blank. Provide a keyword or phrase."
            )
        }
        repository.searchFts(query).map {
            VoiceNoteSummary(
                id = it.id,
                title = it.title,
                folder = it.folderId,
                text = it.text,
                createdAtIso = it.createdAtIso
            )
        }
    }

    /**
     * Returns the most recently recorded and transcribed FieldFlow voice note.
     * @throws AppFunctionElementNotFoundException if no voice notes exist yet.
     */
    suspend fun getLatestNote(): VoiceNoteSummary = withContext(Dispatchers.IO) {
        val latest = repository.getLatestNote()
            ?: throw AppFunctionElementNotFoundException(
                "No voice notes found. Press and hold the FieldFlow floating bubble to record one."
            )
        VoiceNoteSummary(
            id = latest.id,
            title = latest.title,
            folder = latest.folderId,
            text = latest.text,
            createdAtIso = latest.createdAtIso
        )
    }
}

/**
 * S3-3: Minimal 2-tool Android AppFunctions surface for Google Gemini & system agents.
 * KSP generates the AppFunctionService protected by BIND_APP_FUNCTION_SERVICE.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "FieldFlowAppFunctionService",
    appFunctionXmlFileName = "field_flow_app_functions"
)
abstract class BaseFieldFlowAppFunctionService : AppFunctionService() {

    private val ops: FieldFlowAppFunctionsOps by lazy {
        FieldFlowAppFunctionsOps(NoteRepository(FieldFlowDatabase.getInstance(applicationContext).dao()))
    }

    /**
     * Searches recorded FieldFlow voice notes and transcripts by keyword.
     * @param query Non-empty keyword, character name, part number, or phrase to search for.
     * @throws AppFunctionInvalidArgumentException if query is blank.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    internal suspend fun searchVoiceNotes(
        query: String
    ): List<VoiceNoteSummary> = ops.searchVoiceNotes(query)

    /**
     * Returns the most recently recorded and transcribed FieldFlow voice note.
     * @throws AppFunctionElementNotFoundException if no voice notes exist yet.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    internal suspend fun getLatestNote(): VoiceNoteSummary = ops.getLatestNote()
}
