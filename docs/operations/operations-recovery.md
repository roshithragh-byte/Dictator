# Operations and Recovery

## 1. Operational model

The app is primarily on-device. There is no backend service or server-side account system in the current architecture. Operational concerns are therefore local-device concerns: microphone service lifecycle, file storage, model assets, WorkManager jobs, Room state, native inference and OS compatibility.

## 2. Required model assets

Current source expects the following files under app-private storage:

| Asset | Expected path | Used by |
|---|---|---|
| Silero VAD ONNX | `<filesDir>/models/silero_vad.onnx` | `SileroVadSegmenter` |
| Whisper English model | `<filesDir>/models/ggml-base.en-q5_1.bin` | `TranscriptionWorker` / `WhisperSttEngine` |

No complete provisioning workflow is established in the reviewed code. The worker does not download these files. Model import/download, checksum validation, supported versions, storage estimates and license attribution are release requirements.

## 3. Current diagnostic signals

Current source-level signals:
- Foreground notification: "FieldFlow Floating Dictaphone".
- Overlay states: IDLE_DIMMED, RECORDING_HOLD, RECORDING_LOCKED, TRANSCRIBING.
- WorkManager unique work name: `transcribe_<recordingId>`.
- Recording status strings: code comments mention RECORDING, QUEUED, TRANSCRIBED, ERROR; worker writes TRANSCRIBED or ERROR.
- Transcript files: `filesDir/recordings/<folderId>/<sessionStem>.txt`.
- Model paths listed above.

There is no established structured telemetry/event log contract. Avoid logging raw audio or transcript content. Add sanitized diagnostic events for state transitions, duration, queue result, error category, model availability and storage condition.

## 4. Current recovery limitations

The source does not establish automatic recovery for a process death during active recording. The service/controller keeps active recording details in memory; a database row is generally created by the transcription worker after processing. WorkManager can persist queued work, but the app must first successfully finalize the M4A and enqueue it.

The worker currently has early-return paths that do not persist a terminal recording status. The current implementation therefore cannot promise zero data loss or complete recovery.

## 5. Required recovery design

Implement a durable session journal:
1. Create a recording row and stable UUID before capture begins.
2. Persist state, start time, expected audio path and last successful file/segment checkpoint.
3. On normal stop, finalize codec/muxer, validate the audio file and atomically mark it QUEUED.
4. Enqueue unique work by recording ID.
5. Worker records each segment result idempotently and advances a checkpoint.
6. On app startup, scan for RECORDING, FINALIZING, QUEUED and TRANSCRIBING rows.
7. Validate audio file existence/size/container readability.
8. Recover valid finalized audio into QUEUED; mark incomplete/corrupt audio INTERRUPTED or RECOVERY_REQUIRED and preserve it for user action.
9. Reconcile Room metadata, transcript files, structured docs and FTS rows.
10. Never silently delete partial user audio.

Force-stop, reboot, process kill, OS reclaim, low-storage, microphone privacy toggle, phone call/audio focus loss and app upgrade are distinct scenarios and need explicit expected outcomes.

## 6. Troubleshooting matrix

| Symptom | Likely area | Diagnostic action |
|---|---|---|
| Bubble does not appear | Overlay app-op / service | Check Settings.canDrawOverlays, service start and WindowManager errors |
| Recording fails to start | RECORD_AUDIO / FGS restrictions / AudioRecord | Check runtime grant, foreground notification, API-level policy and AudioRecord state |
| M4A exists but no transcript | WorkManager / VAD / model / JNI | Inspect work state, file validity, ONNX availability, Whisper model and native linkage |
| Placeholder transcript appears | Whisper stub or missing model | Inspect CMake build output and ensure release uses real whisper.cpp |
| Transcript empty but worker succeeded | Empty decode or no VAD segments | Treat as failed/needs-attention, inspect source audio and segmenter |
| Search returns no result | FTS index/file mismatch | Inspect recording row, FTS row and canonical TXT file |
| Gemini transform unavailable | ML Kit GenAI feature status | Report unavailable/download state; preserve raw text |
| AppFunctions not discoverable | API level / generated service metadata | Verify API 36+, KSP output, manifest and function XML |
| Share fails | FileProvider paths/URI grant | Verify provider authority, path XML, MIME and grant flag |
| PDF overwritten | Export filename collision | Use unique export identity and inspect output directory |

## 7. Data retention and deletion

The current implementation does not define a full deletion or retention policy. Before release, decide:
- Whether audio is retained indefinitely.
- Whether users can delete audio but retain transcript, or vice versa.
- Whether deleting a note removes Room, FTS, TXT, Markdown, PDF and mirrored MediaStore copies.
- Whether model assets can be removed independently.
- How temporary files and failed exports are cleaned.
- Whether trash/undo is required.
- How users export data before uninstalling, given backup is disabled.

## 8. Operational release checklist

- Model assets available, versioned and checksum-verified.
- Real Whisper JNI loaded; stub build rejected.
- Disk-space thresholds and low-storage UI tested.
- WorkManager queue visible and recoverable.
- Crash/process-death recovery implemented and fault-tested.
- No sensitive content in logs.
- Data deletion/export/restore policy implemented or explicitly documented as a limitation.
- Device compatibility and battery/thermal results attached to release evidence.
