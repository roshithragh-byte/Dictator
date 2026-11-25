package com.fieldflow.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.log10
import kotlin.math.sqrt

data class CaptureResult(
    val recordingId: String,
    val audioFile: File,
    val durationMs: Long
)

/**
 * Thread-safe circular PCM ring buffer storing the latest [capacity] audio samples.
 */
class CircularPcmRingBuffer(val capacity: Int = AudioCaptureController.SAMPLE_RATE_HZ * 2) {
    private val lock = Any()
    private val buffer = ShortArray(capacity)
    private var writeIndex = 0
    private var isFilled = false

    fun write(samples: ShortArray, count: Int = samples.size) {
        synchronized(lock) {
            val toWrite = minOf(count, samples.size)
            for (i in 0 until toWrite) {
                buffer[writeIndex] = samples[i]
                writeIndex = (writeIndex + 1) % capacity
                if (writeIndex == 0) isFilled = true
            }
        }
    }

    fun drain(): ShortArray {
        synchronized(lock) {
            val total = if (isFilled) capacity else writeIndex
            if (total == 0) return ShortArray(0)
            val out = ShortArray(total)
            if (isFilled) {
                val tail = capacity - writeIndex
                System.arraycopy(buffer, writeIndex, out, 0, tail)
                System.arraycopy(buffer, 0, out, tail, writeIndex)
            } else {
                System.arraycopy(buffer, 0, out, 0, total)
            }
            writeIndex = 0
            isFilled = false
            return out
        }
    }

    val currentSize: Int
        get() = synchronized(lock) { if (isFilled) capacity else writeIndex }

    fun clear() {
        synchronized(lock) {
            writeIndex = 0
            isFilled = false
        }
    }
}

/**
 * S1-4: 16 kHz mono AudioRecord controller with a 2-second circular pre-roll ring buffer
 * and incremental AAC (.m4a) encoding into app-private filesDir/recordings/<folderId>/.
 */
class AudioCaptureController(private val context: Context) {

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val PRE_ROLL_SECONDS = 2
        private const val RING_CAPACITY_SAMPLES = SAMPLE_RATE_HZ * PRE_ROLL_SECONDS
        private const val AAC_BITRATE = 32_000
        private const val READ_CHUNK_SAMPLES = 1024
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _amplitudeDb = MutableStateFlow(-60f)
    val amplitudeDb: StateFlow<Float> = _amplitudeDb.asStateFlow()

    // 2-second circular PCM ring buffer
    private val ringBuffer = CircularPcmRingBuffer(RING_CAPACITY_SAMPLES)

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    @Volatile
    private var isWritingToFile = false
    private var activeRecordingId: String? = null
    private var activeOutputFile: File? = null
    private var recordingStartTimeMs: Long = 0L

    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var muxerTrackIndex = -1
    private var muxerStarted = false
    private var totalSamplesEncoded = 0L

    /**
     * Arms the 16 kHz AudioRecord stream so the 2-second pre-roll ring buffer stays warm
     * while the floating overlay is active.
     */
    @SuppressLint("MissingPermission")
    fun arm() {
        if (audioRecord != null) return

        val minBufBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferBytes = maxOf(minBufBytes, READ_CHUNK_SAMPLES * 2 * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferBytes
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }

        audioRecord = recorder
        recorder.startRecording()

        captureJob = scope.launch {
            val chunk = ShortArray(READ_CHUNK_SAMPLES)
            while (isActive) {
                val read = recorder.read(chunk, 0, chunk.size)
                if (read > 0) {
                    updateAmplitudeDb(chunk, read)
                    if (isWritingToFile) {
                        encodePcmToAac(chunk, read)
                    } else {
                        ringBuffer.write(chunk, read)
                    }
                }
            }
        }
    }

    /**
     * Begins writing to a new .m4a file in filesDir, flushing the 2-second pre-roll buffer first
     * so the opening word is never clipped.
     */
    @Synchronized
    fun startCapture(folderId: String): String {
        arm()
        val recId = UUID.randomUUID().toString()
        val folderDir = File(context.filesDir, "recordings/$folderId").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val outFile = File(folderDir, "${stamp}_$recId.m4a")

        initAacEncoder(outFile)
        val preRoll = ringBuffer.drain()
        if (preRoll.isNotEmpty()) {
            encodePcmToAac(preRoll, preRoll.size)
        }

        activeRecordingId = recId
        activeOutputFile = outFile
        recordingStartTimeMs = System.currentTimeMillis()
        isWritingToFile = true
        return recId
    }

    @Synchronized
    fun stopCapture(): CaptureResult? {
        if (!isWritingToFile) return null
        isWritingToFile = false

        val recId = activeRecordingId ?: return null
        val file = activeOutputFile ?: return null
        val duration = System.currentTimeMillis() - recordingStartTimeMs

        finishAacEncoder()
        activeRecordingId = null
        activeOutputFile = null
        return CaptureResult(recId, file, duration)
    }

    fun release() {
        stopCapture()
        captureJob?.cancel()
        audioRecord?.apply {
            runCatching { stop() }
            runCatching { release() }
        }
        audioRecord = null
        ringBuffer.clear()
        scope.cancel()
    }

    private fun initAacEncoder(outFile: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE_HZ, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, READ_CHUNK_SAMPLES * 4)
        }
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        mediaMuxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxerTrackIndex = -1
        muxerStarted = false
        totalSamplesEncoded = 0L
    }

    private fun encodePcmToAac(samples: ShortArray, count: Int) {
        val codec = mediaCodec ?: return
        val byteBuf = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) byteBuf.putShort(samples[i])
        val pcmBytes = byteBuf.array()

        var offset = 0
        while (offset < pcmBytes.size) {
            val inIdx = codec.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                val inputBuffer = codec.getInputBuffer(inIdx) ?: break
                inputBuffer.clear()
                val toCopy = minOf(inputBuffer.remaining(), pcmBytes.size - offset)
                inputBuffer.put(pcmBytes, offset, toCopy)
                val ptsUs = (totalSamplesEncoded * 1_000_000L) / SAMPLE_RATE_HZ
                codec.queueInputBuffer(inIdx, 0, toCopy, ptsUs, 0)
                totalSamplesEncoded += (toCopy / 2)
                offset += toCopy
            }
            drainCodecOutput(codec, endOfStream = false)
        }
    }

    private fun finishAacEncoder() {
        val codec = mediaCodec ?: return
        val inIdx = codec.dequeueInputBuffer(10_000)
        if (inIdx >= 0) {
            val ptsUs = (totalSamplesEncoded * 1_000_000L) / SAMPLE_RATE_HZ
            codec.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
        drainCodecOutput(codec, endOfStream = true)

        runCatching { codec.stop() }
        runCatching { codec.release() }
        mediaCodec = null

        runCatching {
            if (muxerStarted) mediaMuxer?.stop()
            mediaMuxer?.release()
        }
        mediaMuxer = null
        muxerStarted = false
    }

    private fun drainCodecOutput(codec: MediaCodec, endOfStream: Boolean) {
        val bufferInfo = MediaCodec.BufferInfo()
        val muxer = mediaMuxer ?: return
        while (true) {
            val outIdx = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        muxerTrackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outIdx >= 0 -> {
                    val encoded = codec.getOutputBuffer(outIdx)
                    if (encoded != null && bufferInfo.size > 0 && muxerStarted &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    ) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(muxerTrackIndex, encoded, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
                else -> if (!endOfStream) break
            }
        }
    }

    private fun updateAmplitudeDb(samples: ShortArray, count: Int) {
        var sum = 0.0
        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        val rms = sqrt(sum / count)
        val db = if (rms > 1.0) (20.0 * log10(rms / Short.MAX_VALUE)).toFloat() else -60f
        _amplitudeDb.value = db.coerceIn(-60f, 0f)
    }
}
