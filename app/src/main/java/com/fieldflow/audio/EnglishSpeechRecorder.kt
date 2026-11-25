package com.fieldflow.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Dedicated English Speech Recognizer & Audio Capture controller.
 * Listens continuously for English speech ("en-US"), streaming real-time text,
 * and saves the accompanying audio.
 */
class EnglishSpeechRecorder(
    private val context: Context,
    private val audioCaptureController: AudioCaptureController? = null
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _liveText = MutableStateFlow("")
    val liveText: StateFlow<String> = _liveText.asStateFlow()

    private val accumulatedText = StringBuilder()
    var lastRecordedAudioFile: File? = null
        private set

    fun start() {
        if (_isRecording.value) return
        _isRecording.value = true
        accumulatedText.clear()
        _liveText.value = ""
        lastRecordedAudioFile = null

        audioCaptureController?.startCapture("A_Voice_Notes")

        mainHandler.post {
            startRecognizer()
        }
    }

    private fun startRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _liveText.value = ""
            return
        }

        runCatching {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        if (_isRecording.value) {
                            // If paused or no match, resume listening to capture continuous sentences
                            mainHandler.postDelayed({
                                restartListening()
                            }, 150)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                            accumulatedText.append(text)
                            _liveText.value = accumulatedText.toString()
                        }
                        if (_isRecording.value) {
                            restartListening()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim().orEmpty()
                        if (partial.isNotBlank()) {
                            val current = if (accumulatedText.isNotEmpty()) "$accumulatedText $partial" else partial
                            _liveText.value = current
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            restartListening()
        }
    }

    private fun restartListening() {
        if (!_isRecording.value) return
        runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    fun stop(): String {
        _isRecording.value = false
        val captureResult = audioCaptureController?.stopCapture()
        lastRecordedAudioFile = captureResult?.audioFile

        mainHandler.post {
            runCatching {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
        }

        return _liveText.value.trim()
    }

    fun destroy() {
        _isRecording.value = false
        mainHandler.post {
            runCatching {
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
        }
        audioCaptureController?.release()
    }
}
