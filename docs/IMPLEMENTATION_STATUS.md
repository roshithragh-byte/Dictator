# Implementation Status and Production Readiness

**Baseline commit:** `e2517adda8203d5a24b2e1b9c48465ee9d44fab3`  
**Branch reviewed:** `main`  
**Review type:** Static source inspection; no build or device execution.

## Executive status

The repository contains a multi-module Android implementation covering the major product areas. It is not yet supportable to call the application production-ready. Source presence is not equivalent to working end-to-end behavior.

The most important release blocker is the Whisper JNI build: CMake deliberately builds a stub when `core/stt/src/main/cpp/whisper.cpp` is absent, and that stub returns a placeholder string. The transcription worker can then persist a placeholder or mark an empty decode as successful. This must be corrected before claiming offline transcription.

## Capability matrix

| Capability | Source evidence | Status | Production evidence still required |
|---|---|---|---|
| Floating overlay | `OverlayCaptureService` | Implemented, unverified | Device interaction, lifecycle, screen-off and OEM behavior |
| Hold/drag/lock gestures | Touch listener in overlay service | Implemented, unverified | Deterministic gesture tests using actual event timing/density |
| Foreground microphone service | Manifest service type and service startup | Implemented, unverified | API 30–current OS permission/start restrictions and notification behavior |
| PCM capture and AAC/M4A | `AudioCaptureController` | Implemented, unverified | Long-session integrity, encoder error handling, interruption handling |
| 2-second pre-roll | `CircularPcmRingBuffer` | Implemented, unverified | Concurrency and sample-boundary tests |
| Silero VAD | `SileroVadSegmenter` | Implemented with energy fallback | Actual ONNX asset, model I/O compatibility, accuracy and memory profiling |
| Whisper JNI | `WhisperSttEngine`, `whisper_jni.cpp` | **Blocked / stub-capable** | Add and pin whisper.cpp source; build and run real inference on supported ABIs |
| Model provisioning | Worker expects files under `filesDir/models` | **Incomplete** | Download/import flow, integrity hash, versioning, disk-space and license handling |
| WorkManager queue | `TranscriptionWorker.enqueue` | Implemented, unverified | Retry policy, constraints, cancellation, duplicate and process-death tests |
| Transcript file writer | `AtomicTranscriptFileWatcher` | Implemented, consistency gaps | Atomic replacement guarantees, serialization, idempotency and failure recovery |
| Room persistence and FTS | Entities, DAO, database, repository | Implemented, schema hardening needed | Exported schemas, migrations, FK/index policy, FTS correctness and integration tests |
| Compose editor | `NoteEditorScreen`, ViewModel | Implemented, unverified | UI/instrumentation coverage and save/error behavior |
| Platform SpeechRecognizer path | `EnglishSpeechRecorder` | Implemented, separate path | Product decision, offline/network semantics and consistent persistence |
| Gemini Nano transforms | `DocumentTransformer` | Implemented with fallback | Device/model availability, output quality, cancellation and model lifecycle |
| AppFunctions | Service and two operations | Implemented, security/product limits open | API 36+ install/invocation test, caller policy, disclosure and bounded results |
| PDF/print/share | `AndroidDocumentExporter` | Implemented, unverified | Large documents, file collisions, lifecycle and rendering tests |
| Quick Settings tile | `FieldFlowTileService` | Implemented, state-sync gap | Real tile state behavior and service lifecycle |
| Backup/restore | Manifest disables backup | Not implemented / backup disabled | Explicit product decision and user-facing data-loss disclosure |
| Crash recovery | No durable recording session journal observed | **Not established** | Recovery design and fault-injection tests |

## P0 release blockers

1. **Native STT is not guaranteed to be real.** CMake builds a stub if the Whisper checkout is missing. The JNI stub returns a placeholder transcript. The build must fail closed for release variants if real Whisper is not linked.
2. **Worker success semantics are unsafe.** Missing model assets currently produce placeholder text; empty decoded audio returns success without a persisted terminal state. A worker must not mark a recording `TRANSCRIBED` unless real transcription output has been persisted.
3. **Build configuration is machine-specific.** `app/build.gradle.kts` and `core/appfunctions/build.gradle.kts` refer to `/home/machinerg/Android/Sdk/platforms/android-36.1/android.jar`. Replace this with a documented, reproducible compile SDK / dependency arrangement.
4. **Two independent recording paths exist.** The overlay uses `AudioCaptureController` + WorkManager/Whisper; the main editor uses Android `SpeechRecognizer` + `AudioCaptureController`. Product intent, source of truth and behavior when SpeechRecognizer is unavailable must be made explicit.
5. **Crash recovery is not implemented by the observed design.** Recording state is held in service/controller memory; the recording row is generally created after transcription. A killed process can leave an incomplete M4A with no durable state record.
6. **Production data lifecycle is incomplete.** No delete/retention/export-import/restore contract was found. Backup is explicitly disabled in the manifest.

## P1 hardening

- Export Room schemas and implement explicit migrations.
- Replace free-form status strings with a constrained state model and documented transitions.
- Add recording/session IDs and idempotency guarantees to transcript append and worker processing.
- Serialize file writes per recording; use robust atomic replacement and fsync/close guarantees as appropriate.
- Validate and normalize all path-derived IDs and filenames.
- Bound AppFunctions result count and transcript payload size; define access and user-consent policy.
- Add FTS query validation and tests for operators, quotes, punctuation, empty input and Unicode.
- Add model download/import, checksum verification, version compatibility and deletion controls.
- Add explicit audio-focus/interruption handling, storage quota checks, thermal/battery budgets and observability.
- Synchronize Quick Settings tile state with actual service/overlay state.
- Add lifecycle-safe PDF/print handling and unique export filenames.

## Product decisions required

1. Is Android `SpeechRecognizer` a supported online/OS-provided convenience mode, or should the product expose only local Whisper transcription?
2. Should recordings be created and visible immediately, before transcription, including `RECORDING`, `QUEUED`, `TRANSCRIBING`, `TRANSCRIBED`, `FAILED`, `INTERRUPTED`?
3. Are model assets downloaded in-app, imported by the user, or provisioned by a release package?
4. Is public Documents mirroring a supported v1 feature, and where is its user preference persisted?
5. What is the supported minimum Android version for AppFunctions? The app minSdk is 30, while the service class is annotated `@RequiresApi(36)`.
6. Should Dictator or FieldFlow be the public product name?
7. Is backup disabled permanently, or is a backup/export/restore feature required before release?

## Evidence policy

This status is based on source inspection only. A status may be upgraded to **Verified** only with a reproducible test result that identifies device/API level, build variant, model assets and test procedure. A passing JVM test does not verify Android service policy, codec behavior, JNI linkage or on-device model availability.
