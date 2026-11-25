package com.fieldflow.transform

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

enum class TransformTemplate(val label: String, val instruction: String) {
    CLEAN_DICTATION(
        label = "Clean Dictation",
        instruction = "Remove filler words (um, uh, like), fix punctuation, and preserve the speaker's exact meaning in clean paragraphs."
    ),
    STORY_PROSE(
        label = "Story Prose",
        instruction = "Format this spoken draft into cohesive narrative prose while keeping every character detail, setting, and plot beat."
    ),
    CODE_AND_SPECS(
        label = "Code & Specs",
        instruction = "Organize this spoken engineering note into Markdown headings: Goal, Components/APIs, Edge Cases, and Next Steps."
    ),
    WORK_LOG(
        label = "Work Log",
        instruction = "Format this field note into a concise Markdown Work Log with Observations, Measurements/Parts, and Follow-Up Actions."
    ),
    CHECKLIST(
        label = "Checklist",
        instruction = "Extract every actionable task or item mentioned into a clean Markdown `- [ ]` checklist."
    )
}

interface LocalAiClient {
    suspend fun isLocalNpuAvailable(): Boolean
    suspend fun generate(prompt: String): Result<String>
}

/**
 * S3-2: On-device Gemini Nano client via ML Kit GenAI Prompt API (AICore).
 * Runs out-of-process on the device NPU so it never competes with whisper.cpp heap memory.
 */
class MlKitGeminiNanoClient : LocalAiClient {

    private val model: GenerativeModel by lazy { Generation.getClient() }

    override suspend fun isLocalNpuAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            model.checkStatus() == FeatureStatus.AVAILABLE
        }.getOrDefault(false)
    }

    override suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val status = model.checkStatus()
            require(status == FeatureStatus.AVAILABLE) {
                "Gemini Nano is not ready on this device (status=$status)."
            }
            val response = model.generateContent(prompt)
            response.candidates.firstOrNull()?.text?.trim()
                ?: error("Empty response from Gemini Nano")
        }
    }
}

class DocumentTransformer(
    private val aiClient: LocalAiClient = MlKitGeminiNanoClient()
) {
    suspend fun transform(
        rawTranscript: String,
        template: TransformTemplate
    ): Result<String> {
        if (rawTranscript.isBlank()) {
            return Result.failure(IllegalArgumentException("Transcript is empty."))
        }
        val prompt = buildString {
            appendLine("Task: ${template.instruction}")
            appendLine("Rules: Do not invent facts or numbers not present in the transcript. Output Markdown only.")
            appendLine()
            appendLine("Transcript:")
            appendLine(rawTranscript)
        }
        val aiResult = aiClient.generate(prompt)
        if (aiResult.isSuccess) {
            return aiResult
        }
        // Graceful fallback for emulators or devices without AICore / Gemini Nano
        return Result.success(fallbackTransform(rawTranscript, template))
    }

    fun fallbackTransform(rawTranscript: String, template: TransformTemplate): String {
        val cleanRaw = rawTranscript.replace(Regex("(?i)\\b(um|uh|like|you know)\\b"), "").trim()
        return when (template) {
            TransformTemplate.CLEAN_DICTATION -> {
                cleanRaw.lines().filter { it.isNotBlank() }.joinToString("\n\n") { line ->
                    line.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
            TransformTemplate.STORY_PROSE -> {
                "## Story Prose Draft\n\n" + cleanRaw.lines().filter { it.isNotBlank() }.joinToString("\n\n")
            }
            TransformTemplate.CODE_AND_SPECS -> {
                "## Specifications & Architecture\n\n### Goal\n$cleanRaw\n\n### Components & APIs\n- Core Engine\n- Data Models\n\n### Edge Cases & Next Steps\n- Verification & Tests"
            }
            TransformTemplate.WORK_LOG -> {
                "## Field Work Log\n\n- **Timestamp**: ${Instant.now()}\n- **Observations**:\n  $cleanRaw\n\n- **Follow-Up Actions**:\n  - [ ] Review field report"
            }
            TransformTemplate.CHECKLIST -> {
                "## Action Checklist\n\n" + cleanRaw.lines().filter { it.isNotBlank() }.joinToString("\n") { line ->
                    "- [ ] ${line.trim().removePrefix("-").removePrefix("*").trim()}"
                }
            }
        }
    }
}
