# Runtime Flows

This document describes behavior visible in the current source and highlights gaps where behavior is not yet guaranteed.

## 1. Overlay activation and recording

1. The user grants microphone and overlay permissions through onboarding.
2. The app starts `OverlayCaptureService` as a foreground service.
3. The service creates a low-importance notification channel and starts foreground with the microphone service type.
4. The service creates a 56dp `TYPE_APPLICATION_OVERLAY` view with `FLAG_NOT_FOCUSABLE`.
5. On service start, if overlay permission is available, it attaches the bubble and calls `AudioCaptureController.arm()`.
6. `arm()` creates a 16 kHz mono PCM16 `AudioRecord` and continuously reads 1024-sample chunks on an IO coroutine.
7. While not recording, samples are written to a 2-second circular pre-roll buffer. While recording, samples are encoded as AAC into an M4A container.
8. A touch-down schedules a 120 ms hold timer. Movement beyond 8dp cancels hold and moves the bubble. A 48dp upward movement while holding changes the visual state to locked recording.
9. Releasing while recording stops capture and enqueues unique WorkManager work named `transcribe_<recordingId>`.
10. The overlay visual state returns to idle after a fixed 350 ms delay; this is not tied to WorkManager completion.

### Gesture contract currently encoded

| Gesture | Current implementation |
|---|---|
| Touch down while idle | Schedule 120 ms hold timer |
| Hold timer expires | Enter `RECORDING_HOLD`, start audio capture |
| Move > 8dp while idle | Cancel hold, drag overlay |
| Move upward > 48dp while holding | Enter `RECORDING_LOCKED` |
| Release while holding/locked | Stop capture and enqueue transcription |
| Release while idle | Launch main editor |
| Drag release | Snap bubble to nearest horizontal edge |

The touch logic and comments are in `OverlayCaptureService`. The state is currently in-memory; it is not persisted as a durable recording session state machine.

## 2. Audio encoding

- Input: `AudioRecord`, `VOICE_RECOGNITION`, 16,000 Hz, mono, PCM16.
- Read chunk: 1,024 samples.
- Pre-roll: 32,000 samples (2 seconds).
- Encoding: Android `MediaCodec`, AAC-LC, 32 kbps.
- Container: Android `MediaMuxer`, MPEG-4 / `.m4a`.
- Storage: `filesDir/recordings/<folderId>/<timestamp>_<uuid>.m4a`.

The 2-second pre-roll is drained into the encoder when recording starts. The controller calculates media presentation timestamps from total encoded samples.

Unresolved: encoder backpressure, read/encode race behavior at start/stop, codec errors, interruption recovery, disk-full behavior, and ensuring the M4A is finalized on all lifecycle paths.

## 3. Overlay transcription worker

1. Enqueue payload includes recording ID, folder ID, audio path, auto-copy flag and public-mirror flag.
2. Worker validates required identifiers and checks that the audio file exists.
3. Worker decodes the M4A audio track with `MediaExtractor` and `MediaCodec` into a ShortArray.
4. Worker loads `filesDir/models/silero_vad.onnx`. If the model is missing or fails to load, VAD uses the current RMS/peak-energy fallback.
5. VAD processes 512-sample frames (32 ms at 16 kHz), with a 0.50 speech threshold, 300 ms minimum speech and 900 ms silence timeout.
6. Worker loads `filesDir/models/ggml-base.en-q5_1.bin` through `WhisperSttEngine`.
7. For each segment, it invokes JNI transcription, appends timestamped text through `AtomicTranscriptFileWatcher`, and collects segment entities.
8. Worker upserts recording metadata, segment rows and FTS content.
9. Worker releases the Whisper context in a finally block and optionally copies the combined transcript to clipboard.

### Current failure semantics that require correction

- Missing audio file returns WorkManager failure.
- Empty decoded PCM currently returns success without recording a terminal failure state.
- Missing Whisper model produces a placeholder string and can still lead to a `TRANSCRIBED` recording status.
- The native JNI stub can return a placeholder transcript if whisper.cpp is not linked.
- The worker does not currently distinguish a real transcript from a placeholder.
- A broad catch writes an `ERROR` recording row with an empty transcript path and returns failure.
- No explicit backoff, network/storage constraints, cancellation policy or retry budget is configured in the enqueue method.

These are observed behaviors, not desired production semantics.

## 4. In-app editor recording path

1. `MainActivity` constructs an `EnglishSpeechRecorder` with an `AudioCaptureController`.
2. Start sets recording state, clears accumulated text and starts audio capture.
3. It creates Android `SpeechRecognizer` and requests free-form English recognition with partial results.
4. Final recognition results are accumulated; partial results are shown in live text.
5. On recognition errors/results, it schedules another listening attempt while recording remains active.
6. Stop stops audio capture, stores the audio file reference and returns the latest live text.
7. The UI may save a named note through `NoteEditorViewModel` and `NoteRepository`.

This path is not the same as the overlay's offline Whisper worker. The Android SpeechRecognizer implementation does not itself establish offline recognition or a durable retryable transcription job.

## 5. Transform flow

1. The editor passes the current raw text and selected `TransformTemplate` to `DocumentTransformer`.
2. Empty input returns failure.
3. The transformer builds a prompt with template instructions and a no-invented-facts rule.
4. `MlKitGeminiNanoClient` checks ML Kit GenAI Prompt API feature status and calls `generateContent` when available.
5. If generation fails, `DocumentTransformer` returns a deterministic template fallback as a successful result.
6. If a recording ID is selected, the ViewModel asks the repository to save the Markdown document and update FTS.

The fallback is a structural formatter, not semantically equivalent to Gemini Nano. The UI currently does not receive a distinct "AI generated" versus "fallback generated" result state.

## 6. AppFunctions flow

- The generated AppFunction service is declared exported and protected by `android.permission.BIND_APP_FUNCTION_SERVICE`.
- The service exposes `searchVoiceNotes(query)` and `getLatestNote()`.
- Operations validate blank search queries, call `NoteRepository`, and return a `VoiceNoteSummary` containing ID, title, folder, full text and ISO timestamp.
- Search/latest-note results are built from Room metadata and transcript file reads.

Production contract still needed: maximum result count, maximum text size, pagination, caller/data disclosure policy, user consent, redaction, access logging, and behavior for missing transcript files.

## 7. Export flow

- Print: Markdown-like lines are HTML-escaped and converted to a basic HTML document loaded into WebView; WebView creates a PrintDocumentAdapter and Android PrintManager presents the print flow.
- PDF: `PdfDocument` writes A4 pages under `filesDir/recordings/exports`; a FileProvider URI is returned.
- Share: ACTION_SEND with the supplied URI and read permission is wrapped in a chooser.
- Maps: `geo:` intent targets Google Maps; a web Maps URL is used if the package cannot resolve.

The current PDF writer uses a title-derived filename and may overwrite a previous export with the same title. Resource cleanup and concurrent export behavior require hardening.

## 8. Desired production recording lifecycle (not yet implemented as a durable state machine)

The following is a recommended target contract, not a claim about current code:

```text
IDLE
  -> STARTING
  -> RECORDING
  -> FINALIZING
  -> QUEUED
  -> TRANSCRIBING
  -> TRANSCRIBED

Failure/interrupt branches:
STARTING/RECORDING -> INTERRUPTED
FINALIZING         -> RECOVERY_REQUIRED
QUEUED/TRANSCRIBING -> RETRY_WAIT -> TRANSCRIBING
Any non-recoverable failure -> FAILED
```

Persist the recording ID and state before audio capture begins. Every transition should be validated and timestamped. Finalization must make the audio file durable before queuing work. Worker output must be idempotent by recording ID and segment ID. Only successful persistence of genuine transcript text may transition a recording to `TRANSCRIBED`.

## 9. Desired idempotency and ordering

- Work identity: stable `recordingId`.
- Segment identity: stable `recordingId + segmentIndex`.
- Transcript append: persist segment output and processing checkpoint so retries cannot append duplicate text.
- FTS: update from canonical persisted text in the same logical transaction as metadata/segment changes, or reconcile from canonical files.
- File naming: never derive a filesystem path from unchecked external/user-provided strings.
- Worker cancellation: preserve checkpoint and resume safely, or explicitly fail without losing the original audio.
