package com.fieldflow.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.abs

data class SpeechSegment(
    val recordingId: String,
    val segmentIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val pcmSamples: ShortArray
)

/**
 * S2-1: Silero VAD segmenter using onnxruntime-android on 16 kHz mono PCM (512-sample = 32 ms frames).
 * Trims silence and splits long locked recordings into SpeechSegment chunks.
 * Falls back to an RMS energy gate if the Silero .onnx model is not yet present on disk.
 */
class SileroVadSegmenter(
    private val speechThreshold: Float = 0.50f,
    private val minSpeechMs: Long = 300L,
    private val silenceTimeoutMs: Long = 900L
) : AutoCloseable {
    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SAMPLES = 512 // 32 ms at 16 kHz
        private const val FRAME_MS = 32L
        private const val STATE_DIM_0 = 2
        private const val STATE_DIM_1 = 1
        private const val STATE_DIM_2 = 128
    }

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    fun loadModel(onnxModelFile: File): Boolean {
        if (!onnxModelFile.exists()) return false
        return runCatching {
            env = OrtEnvironment.getEnvironment()
            session = env?.createSession(onnxModelFile.absolutePath, OrtSession.SessionOptions())
            session != null
        }.getOrDefault(false)
    }

    fun segment(recordingId: String, pcm16kHz: ShortArray): List<SpeechSegment> {
        val segments = mutableListOf<SpeechSegment>()
        if (pcm16kHz.size < FRAME_SAMPLES) return segments

        var stateArray = Array(STATE_DIM_0) { Array(STATE_DIM_1) { FloatArray(STATE_DIM_2) } }
        val activeBuffer = mutableListOf<Short>()
        var inSpeech = false
        var speechStartMs = 0L
        var silenceAccumMs = 0L
        var segmentIdx = 0

        var offset = 0
        var currentMs = 0L
        while (offset + FRAME_SAMPLES <= pcm16kHz.size) {
            val frame = pcm16kHz.copyOfRange(offset, offset + FRAME_SAMPLES)
            val prob = if (session != null && env != null) {
                val (p, nextState) = runSileroFrame(frame, stateArray)
                stateArray = nextState
                p
            } else {
                estimateEnergyProb(frame)
            }

            if (prob >= speechThreshold) {
                if (!inSpeech) {
                    inSpeech = true
                    speechStartMs = currentMs
                }
                silenceAccumMs = 0L
                for (s in frame) activeBuffer.add(s)
            } else if (inSpeech) {
                silenceAccumMs += FRAME_MS
                for (s in frame) activeBuffer.add(s)
                if (silenceAccumMs >= silenceTimeoutMs) {
                    val durationMs = currentMs - speechStartMs
                    if (durationMs >= minSpeechMs) {
                        segments.add(
                            SpeechSegment(
                                recordingId = recordingId,
                                segmentIndex = segmentIdx++,
                                startMs = speechStartMs,
                                endMs = currentMs,
                                pcmSamples = activeBuffer.toShortArray()
                            )
                        )
                    }
                    activeBuffer.clear()
                    inSpeech = false
                    silenceAccumMs = 0L
                }
            }

            offset += FRAME_SAMPLES
            currentMs += FRAME_MS
        }

        if (inSpeech && activeBuffer.isNotEmpty()) {
            val durationMs = currentMs - speechStartMs
            if (durationMs >= minSpeechMs) {
                segments.add(
                    SpeechSegment(
                        recordingId = recordingId,
                        segmentIndex = segmentIdx,
                        startMs = speechStartMs,
                        endMs = currentMs,
                        pcmSamples = activeBuffer.toShortArray()
                    )
                )
            }
        }
        return segments
    }

    override fun close() {
        release()
    }

    fun release() {
        runCatching { session?.close() }
        session = null
    }

    private fun runSileroFrame(
        frame: ShortArray,
        state: Array<Array<FloatArray>>
    ): Pair<Float, Array<Array<FloatArray>>> {
        val ortEnv = env ?: return 0f to state
        val ortSession = session ?: return 0f to state

        val floatInput = FloatArray(FRAME_SAMPLES) { i -> frame[i] / 32768.0f }
        val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floatInput), longArrayOf(1, FRAME_SAMPLES.toLong()))
        val srTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(longArrayOf(SAMPLE_RATE.toLong())), longArrayOf(1))
        val stateTensor = OnnxTensor.createTensor(ortEnv, state)

        return try {
            val inputs = mapOf(
                "input" to inputTensor,
                "sr" to srTensor,
                "state" to stateTensor
            )
            ortSession.run(inputs).use { result ->
                @Suppress("UNCHECKED_CAST")
                val outProb = (result[0].value as Array<FloatArray>)[0][0]
                @Suppress("UNCHECKED_CAST")
                val nextState = result[1].value as Array<Array<FloatArray>>
                outProb to nextState
            }
        } finally {
            inputTensor.close()
            srTensor.close()
            stateTensor.close()
        }
    }

    private fun estimateEnergyProb(frame: ShortArray): Float {
        var peak = 0
        for (s in frame) {
            val a = abs(s.toInt())
            if (a > peak) peak = a
        }
        return if (peak > 900) 0.85f else 0.10f
    }
}
