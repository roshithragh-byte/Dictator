package com.fieldflow.stt

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fieldflow.audio.SileroVadSegmenter
import com.fieldflow.data.FieldFlowDatabase
import com.fieldflow.data.RecordingEntity
import com.fieldflow.data.RecordingFtsEntity
import com.fieldflow.data.TranscriptSegmentEntity
import com.fieldflow.watcher.AtomicTranscriptFileWatcher
import com.fieldflow.watcher.ClipboardHelper
import java.io.File
import java.nio.ByteOrder

/**
 * S2-3: WorkManager CoroutineWorker that decodes recorded .m4a audio -> Silero VAD ->
 * whisper.cpp JNI -> atomic .txt file append -> optional Auto-Copy to Clipboard.
 */
class TranscriptionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_AUDIO_PATH = "audio_path"
        const val KEY_FOLDER_ID = "folder_id"
        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_AUTO_COPY = "auto_copy"
        const val KEY_MIRROR_PUBLIC = "mirror_public"

        fun enqueue(
            context: Context,
            recordingId: String,
            folderId: String,
            audioFile: File,
            autoCopyToClipboard: Boolean = true,
            mirrorToPublicDocuments: Boolean = false
        ) {
            val data = Data.Builder()
                .putString(KEY_RECORDING_ID, recordingId)
                .putString(KEY_FOLDER_ID, folderId)
                .putString(KEY_AUDIO_PATH, audioFile.absolutePath)
                .putBoolean(KEY_AUTO_COPY, autoCopyToClipboard)
                .putBoolean(KEY_MIRROR_PUBLIC, mirrorToPublicDocuments)
                .build()

            val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "transcribe_$recordingId",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        val audioPath = inputData.getString(KEY_AUDIO_PATH) ?: return Result.failure()
        val folderId = inputData.getString(KEY_FOLDER_ID) ?: "A_Field_Logs"
        val recordingId = inputData.getString(KEY_RECORDING_ID) ?: return Result.failure()
        val autoCopy = inputData.getBoolean(KEY_AUTO_COPY, true)
        val mirrorPublic = inputData.getBoolean(KEY_MIRROR_PUBLIC, false)

        val audioFile = File(audioPath)
        if (!audioFile.exists()) return Result.failure()

        val pcm16k = decodeM4aToPcm16k(audioFile)
        if (pcm16k.isEmpty()) return Result.success()

        val vad = SileroVadSegmenter().apply {
            loadModel(File(applicationContext.filesDir, "models/silero_vad.onnx"))
        }
        val segments = vad.segment(recordingId, pcm16k)
        vad.release()
        if (segments.isEmpty()) return Result.success()

        val stt = WhisperSttEngine()
        val modelFile = File(applicationContext.filesDir, "models/ggml-base.en-q5_1.bin")
        val modelLoaded = stt.loadModel(modelFile.absolutePath).isSuccess

        val watcher = AtomicTranscriptFileWatcher(applicationContext)
        val sessionStem = audioFile.nameWithoutExtension
        val combinedText = StringBuilder()
        val dao = FieldFlowDatabase.getInstance(applicationContext).dao()
        val segmentEntities = mutableListOf<TranscriptSegmentEntity>()

        try {
            for ((index, seg) in segments.withIndex()) {
                val text = if (modelLoaded) {
                    stt.transcribe(seg.pcmSamples).getOrDefault("")
                } else {
                    "[Pending offline model: place ggml-base.en-q5_1.bin in filesDir/models/]"
                }
                if (text.isNotBlank()) {
                    val range = "${formatMs(seg.startMs)} - ${formatMs(seg.endMs)}"
                    watcher.appendSegment(folderId, sessionStem, range, text, mirrorPublic)
                    if (combinedText.isNotEmpty()) combinedText.append(" ")
                    combinedText.append(text)
                    segmentEntities.add(
                        TranscriptSegmentEntity(
                            id = "${recordingId}_$index",
                            recordingId = recordingId,
                            segmentIndex = index,
                            startMs = seg.startMs,
                            endMs = seg.endMs,
                            rawText = text
                        )
                    )
                }
            }

            val finalTxtPath = File(applicationContext.filesDir, "recordings/$folderId/$sessionStem.txt").absolutePath
            val now = System.currentTimeMillis()
            val durationMs = segments.lastOrNull()?.endMs ?: 0L

            dao.upsertRecording(
                RecordingEntity(
                    id = recordingId,
                    folderId = folderId,
                    title = sessionStem,
                    audioPath = audioFile.absolutePath,
                    txtPath = finalTxtPath,
                    durationMs = durationMs,
                    createdAtEpochMs = now,
                    status = "TRANSCRIBED"
                )
            )
            if (segmentEntities.isNotEmpty()) {
                dao.insertSegments(segmentEntities)
            }
            dao.upsertFts(
                RecordingFtsEntity(
                    recordingId = recordingId,
                    title = sessionStem,
                    rawText = combinedText.toString(),
                    structuredText = ""
                )
            )
        } catch (e: Exception) {
            dao.upsertRecording(
                RecordingEntity(
                    id = recordingId,
                    folderId = folderId,
                    title = sessionStem,
                    audioPath = audioFile.absolutePath,
                    txtPath = "",
                    durationMs = 0L,
                    createdAtEpochMs = System.currentTimeMillis(),
                    status = "ERROR"
                )
            )
            return Result.failure()
        } finally {
            // CSO/OOM guardrail: explicitly release whisper.cpp before any downstream LLM transform
            stt.release()
        }

        if (autoCopy && combinedText.isNotEmpty()) {
            ClipboardHelper.copyText(applicationContext, sessionStem, combinedText.toString())
        }
        return Result.success()
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    private fun decodeM4aToPcm16k(file: File): ShortArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var trackIndex = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                trackIndex = i
                inputFormat = fmt
                break
            }
        }
        if (trackIndex < 0 || inputFormat == null) {
            extractor.release()
            return ShortArray(0)
        }

        extractor.selectTrack(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()

        val outSamples = mutableListOf<Short>()
        val info = MediaCodec.BufferInfo()
        var eos = false

        try {
            while (!eos) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    while (shortBuf.hasRemaining()) {
                        outSamples.add(shortBuf.get())
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) eos = true
                }
            }
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            extractor.release()
        }
        return outSamples.toShortArray()
    }
}
