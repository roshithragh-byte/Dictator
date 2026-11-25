package com.fieldflow.stt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface SttEngine {
    suspend fun loadModel(modelPath: String): Result<Unit>
    suspend fun transcribe(pcm16kHz: ShortArray): Result<String>
    fun release()
}

/**
 * S2-2: Native whisper.cpp engine wrapper.
 * Loads quantized ggml models (e.g. ggml-tiny.en-q5_1.bin) from app-private filesDir/models/.
 */
class WhisperSttEngine : SttEngine {

    companion object {
        init {
            System.loadLibrary("fieldflow_whisper")
        }
    }

    @Volatile
    private var contextPtr: Long = 0L

    override suspend fun loadModel(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(modelPath)
            require(file.exists()) { "Whisper model not found at $modelPath" }
            release()
            val ptr = nativeLoadModel(file.absolutePath)
            require(ptr != 0L) { "Failed to initialize whisper.cpp context" }
            contextPtr = ptr
        }
    }

    override suspend fun transcribe(pcm16kHz: ShortArray): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val ptr = contextPtr
            require(ptr != 0L) { "Whisper model not loaded" }
            val floats = FloatArray(pcm16kHz.size) { i -> pcm16kHz[i] / 32768.0f }
            nativeTranscribe(ptr, floats).trim()
        }
    }

    override fun release() {
        val ptr = contextPtr
        if (ptr != 0L) {
            nativeFreeModel(ptr)
            contextPtr = 0L
        }
    }

    private external fun nativeLoadModel(modelPath: String): Long
    private external fun nativeTranscribe(ctxPtr: Long, pcmFloat16kHz: FloatArray): String
    private external fun nativeFreeModel(ctxPtr: Long)
}
