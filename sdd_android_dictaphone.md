# Software Design Document (SDD): FieldFlow — Android Floating Dictaphone, AppFunctions & Document Engine

> **Recommended Path (For Future Chats & Sessions):**
> 1. **Methodology:** Follow **Agile (1-Week Vertical Slices) + Spec-Driven Development (SDD)**. Ship and test a working APK on a physical Android phone at the end of every sprint before starting the next.
> 2. **v1 Core Wedge First:** Build **Floating Hold/Lock Bubble (`120 ms` hold, `8dp` drag, Slide-Up to Lock) -> Offline `whisper.cpp` `.txt` Watcher -> Built-in Text Editor + Auto-Copy -> Local Gemini Nano Transforms -> Minimal 2-Tool `AppFunctions` -> `PrintManager` PDF & Share Sheet**. Keep Workspace OAuth, Places SDK, and `.docx` deferred to **v1.1**.
> 3. **Current Checkpoint:** Historical planning checkpoint. See [Implementation Baseline Addendum](#10-implementation-baseline-addendum-2026-11-26) and `docs/IMPLEMENTATION_STATUS.md` for the reviewed repository status.

**Target Platform:** Android (API 30+ / Android 11+, with Android 14+ foreground microphone & Android 16+ AppFunctions support)  
**Design Inspiration:** Handheld dictaphone ergonomics (inspired by the [Sony ICD-SX712 / XJS 5000](https://www.homebrewaudio.com/11787/the-xjs-5000-dictaphone-from-suits/)) reimagined as an **always-accessible Android floating overlay button** that works over any app or home screen, paired with an automatic audio-to-text file watcher, built-in text editor, on-device Gemini Nano, **Android AppFunctions**, printing, and sharing.  
**Development Methodology:** Agile (1-Week Vertical Slices) + Spec-Driven Development (SDD) + gstack Pre-Code Hardening

---

## 1. Product Vision & Scope (v1 Wedge vs. v1.1)

FieldFlow is an **ambient thought-to-file capture tool** for **story/book writers, coders who prefer speaking over typing, field workers, and everyday note-takers**. Its primary interface is a **single draggable floating button** over any app or home screen: **press and hold** (or **slide-up to lock**) to record, release to transcribe offline into `.txt` files, and tap to open the built-in text editor for structured transforms, printing, and sharing.

### v1 Core Wedge (Ship First)
1. **Floating Hold/Lock Overlay:** Draggable bubble over any app or home screen (`120 ms` hold-to-record, `Slide-Up to Lock` for long dictations, short tap for editor).
2. **Offline Audio-to-Text Watcher:** `Silero VAD` + `whisper.cpp` (JNI) appends timestamped transcripts to `.txt` files with optional auto-copy to clipboard.
3. **Built-in Text Editor & Local Gemini Nano Transforms:** Edit `.txt` directly or apply templates (`Clean Dictation`, `Story Prose`, `Code & Specs`, `Work Log`, `Checklist`) via on-device [ML Kit GenAI / AICore](https://developers.google.com/ml-kit/genai/agent/android/define-tools) (with explicit opt-in cloud fallback).
4. **Minimal Android AppFunctions:** Exposes `searchVoiceNotes` and `getLatestNote` via [Android AppFunctions](https://developer.android.com/ai/appfunctions/add-appfunctions) (`androidx.appfunctions`) so Google Gemini can query FieldFlow notes.
5. **Print & Share (Zero-Auth):** Android `PrintManager` (`PdfDocument`) printouts and `Intent.ACTION_SEND` / `geo:` intents for sharing to Gmail, Drive, Docs, Keep, Maps, or WhatsApp.

### Deferred to v1.1 (Out of v1 Scope)
- Direct Google Workspace OAuth APIs (Drive/Docs/Calendar/Tasks auto-sync) and Google Places SDK (v1 uses zero-setup Android Share & `geo:` Maps intents).
- Custom `.docx` XML exporter (v1 ships `.txt`, `.md`, and `.pdf`).

---

## 2. Functional Requirements

| ID | Requirement | Priority | Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **FR-01** | **Floating Hold/Lock Overlay** | P0 | Draggable `56dp` bubble over any app/home screen (`TYPE_APPLICATION_OVERLAY`). `120 ms` hold (`ACTION_DOWN`) starts recording; **Slide-Up to Lock** enables hands-free recording; release (`ACTION_UP`) stops and queues transcription. |
| **FR-02** | **Android 14+ Foreground Mic Service** | P0 | `OverlayCaptureService` (`microphone` type) starts while the app is foregrounded when the overlay is enabled, staying alive with a persistent notification and `VibrationEffect` feedback. |
| **FR-03** | **VAD + 2s Pre-Roll Ring Buffer** | P0 | 16 kHz mono PCM circular buffer prevents clipping the first word. **Silero VAD** trims leading/trailing silence and splits long locked recordings into speech segments. |
| **FR-04** | **Offline Audio-to-Text Watcher** | P0 | `WorkManager` runs **whisper.cpp** (JNI) on finished segments, appending timestamped lines to canonical `.txt` storage (and optional public `Documents/FieldFlow/` mirror) + optional auto-copy to clipboard. |
| **FR-05** | **Text Editor & Local Gemini Nano Transforms** | P1 | Short tap opens a bottom-sheet/full editor with **Copy**, **Transform**, and **Print/Share** pinned to the thumb bar. Transforms run on-device via Gemini Nano (`AICore`) without holding `whisper.cpp` in RAM simultaneously. |
| **FR-06** | **Direct Printouts & Share Sheet** | P1 | Print any raw or structured note via Android `PrintManager` (`PdfDocument`), or share `.txt`, `.md`, and `.pdf` via `FileProvider` and `Intent.ACTION_SEND`. |
| **FR-07** | **Folder Slots & Full-Text Search** | P1 | Organizes notes into folder slots (`A`–`D` / custom) and indexes all transcripts in Room `@Fts4` for instant search and tap-to-seek audio playback. |
| **FR-08** | **Android AppFunctions for Gemini** | P1 | Exposes `searchVoiceNotes` and `getLatestNote` via `androidx.appfunctions` with agent-ready KDoc and typed `AppFunctionException` recovery hints. |

---

## 3. Non-Functional, Security & Privacy Requirements (CSO Hardened)

- **Overlay Responsiveness & Safety:** Hold-to-record starts in `< 100 ms`; `8dp` touch-slop separates drag from hold; `FLAG_NOT_FOCUSABLE` prevents stealing focus from other apps; auto-dims to `40%` opacity when idle.
- **Data-at-Rest Privacy:** Canonical audio (`.m4a`/`.opus`) and `.txt`/`.md` files are stored in app-private `context.filesDir` (protected by Android File-Based Encryption). Public mirroring to `Documents/FieldFlow/` is **off by default** and controlled by an explicit user toggle.
- **Strict On-Device Default:** `whisper.cpp` and Gemini Nano (`AICore`) run locally; cloud LLM/STT APIs require explicit user opt-in.
- **AppFunctions Trust Boundary:** Generated `AppFunctionService` enforces `android:permission="android.permission.BIND_APP_FUNCTION_SERVICE"` so only `system_server` / authorized agents can bind.
- **Memory & OOM Guardrail:** `whisper.cpp` is explicitly released/unloaded before any fallback local LLM runs in-process; Gemini Nano runs out-of-process inside system `AICore`.
- **Zero Data Loss:** Audio chunks flush incrementally; process-kill recovery finalizes partial files and queues transcription on next launch.

---

## 4. System Architecture (v1 Modules)

| Module | Responsibility | Key Technologies |
| :--- | :--- | :--- |
| `app` | Jetpack Compose Text Editor, Folder browser, Permission onboarding, DI graph | Kotlin, Jetpack Compose, Hilt |
| `core:overlay` | `WindowManager` (`TYPE_APPLICATION_OVERLAY`) draggable bubble, `120 ms` hold + Slide-to-Lock gesture detector, 3 visual states | Android `WindowManager`, `MotionEvent`, `VibrationEffect` |
| `core:audio` | `ForegroundService` (`microphone`), `AudioRecord` 16 kHz stream, 2s ring buffer, Silero VAD, `.m4a`/`.opus` writer | Android `AudioRecord`, `onnxruntime-android` |
| `core:stt` | Native `whisper.cpp` JNI wrapper, model manager, `WorkManager` transcription queue | C++17, Android NDK, `whisper.cpp`, `WorkManager` |
| `core:watcher` | Atomic `.txt` & `.md` writer in `filesDir` + optional `MediaStore` mirror to `Documents/FieldFlow/` | Kotlin Coroutines, `MediaStore` |
| `core:transform` | On-device **Gemini Nano** (`genai-prompt` / `genai-agent`) + explicit opt-in cloud fallback | `com.google.mlkit:genai-prompt`, Retrofit |
| `core:appfunctions` | Exposes `searchVoiceNotes` & `getLatestNote` to Google Gemini via KSP-generated `AppFunctionService` | `androidx.appfunctions:appfunctions`, KSP |
| `core:export` | Print layout renderer (`PrintManager`), `PdfDocument` generator, `FileProvider` Share Sheet & `geo:` Maps intent helper | `PrintManager`, `PdfDocument`, `FileProvider` |
| `core:data` | Room database, `@Fts4` search index, folder slots, user preferences | Room, DataStore |

---

## 5. Data Model & File Layout

### Room Database Schema

| Table | Key Columns | Purpose |
| :--- | :--- | :--- |
| `folder_slots` | `id`, `slot_code` (`A`–`D`), `name`, `default_template` | Organizes notes by role (`Story`, `Code`, `Field`, `Personal`) |
| `recordings` | `id`, `folder_id`, `title`, `audio_path`, `txt_path`, `duration_ms`, `created_at`, `status` | Master record per dictation session (canonical paths in `filesDir`) |
| `transcript_segments` | `id`, `recording_id`, `start_ms`, `end_ms`, `raw_text`, `confidence` | Timestamped chunks for tap-to-seek playback |
| `structured_docs` | `id`, `recording_id`, `template_type`, `markdown_content`, `updated_at` | Non-destructive transformed versions |
| `recording_fts` | `recording_id`, `raw_text`, `structured_text` | Room `@Fts4` virtual table for instant search |

### Canonical Storage Layout
```text
<app-private filesDir>/recordings/
├── A_Story_Notes/
│   ├── 2026-11-25_23-10_Chapter3.m4a
│   ├── 2026-11-25_23-10_Chapter3.txt       <-- Atomic canonical transcript
│   └── 2026-11-25_23-10_Chapter3_prose.md  <-- Structured Story Prose
└── ...
(Optional user-enabled mirror -> Documents/FieldFlow/...)
```

---

## 6. Core Technical Contracts (Kotlin)

### 6.1 Floating Overlay & Audio Capture (`core:overlay` + `core:audio`)
```kotlin
enum class OverlayVisualState {
    IDLE_DIMMED,        // 40% opacity edge-snapped pill
    RECORDING_HOLD,     // Pulsing red/blue halo + timer badge
    RECORDING_LOCKED,   // Locked hands-free state with stop/cancel controls
    TRANSCRIBING        // Amber spinner ring
}

interface FloatingOverlayController {
    val visualState: StateFlow<OverlayVisualState>
    fun showOverlay()
    fun hideOverlay()
    fun onHoldStart()       // Fires after 120ms hold without >8dp drag
    fun onSlideToLock()     // Locks recording hands-free
    fun onStopRecording()   // Stops hold or locked recording
    fun onSingleTap()       // Opens quick bottom-sheet Text Editor
}
```

### 6.2 Android AppFunctions with DevEx Error Contracts (`core:appfunctions`)
```kotlin
@AppFunctionSerializable
data class VoiceNoteSummary(
    val id: String,
    val title: String,
    val folder: String,
    val text: String,
    val createdAtIso: String
)

class FieldFlowAppFunctions(
    private val repository: NoteRepository
) {
    /**
     * Searches FieldFlow voice notes and transcripts by keyword.
     * @param query Non-empty keyword, character name, part number, or phrase.
     * @throws AppFunctionInvalidArgumentException if query is blank.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun searchVoiceNotes(
        appFunctionContext: AppFunctionContext,
        query: String
    ): List<VoiceNoteSummary> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            throw AppFunctionInvalidArgumentException("Query must not be blank.")
        }
        repository.searchFts(query)
    }

    /**
     * Returns the most recently recorded and transcribed FieldFlow voice note.
     * @throws AppFunctionElementNotFoundException if no notes exist yet.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getLatestNote(
        appFunctionContext: AppFunctionContext
    ): VoiceNoteSummary = withContext(Dispatchers.IO) {
        repository.getLatestNote()
            ?: throw AppFunctionElementNotFoundException(
                "No voice notes found. Press and hold the FieldFlow bubble to record one."
            )
    }
}
```

---

## 7. Android Permissions & Manifest Spec

| Permission | Reason |
| :--- | :--- |
| `SYSTEM_ALERT_WINDOW` | Draw the floating hold/lock button over any app or home screen |
| `RECORD_AUDIO` | Microphone capture |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | Keep floating button and mic active in background (Android 14+) |
| `POST_NOTIFICATIONS` | Persistent notification for overlay status |
| `WAKE_LOCK` | Prevent CPU sleep during locked pocket recordings |

*(Generated `AppFunctionService` declares `android:permission="android.permission.BIND_APP_FUNCTION_SERVICE"` and `android:exported="true"`.)*

---

## 8. Development Methodology, Test Matrix & DoD

- **Methodology:** Agile (1-Week Vertical Slices) + Spec-Driven Development (SDD).
- **Definition of Done (DoD):** Compiles cleanly (API 30–35), passes the Test Matrix below, and is verified on a **physical Android device**.

### Minimum Test Matrix
| Layer | What to Test | Target |
| :--- | :--- | :--- |
| **Unit (JVM)** | `120 ms` hold vs. `8dp` drag separation, Slide-to-Lock state machine, 2s PCM ring buffer, `@AppFunction` KSP schema & error mapping | 100% pass on CI |
| **Integration** | `WorkManager` retry on process kill, `whisper.cpp` JNI transcription of 10s/60s `.wav` fixtures, Room `@Fts4` search | Physical device |
| **Field / Device** | Hold & Slide-to-Lock over Chrome/IDE/Home screen, screen-off lock, 30-min battery & thermal check, `PrintManager` PDF preview | Real Android 14–16 phone |

---

## 9. 4-Week Agile Sprint Backlog (Hardened v1)

### Sprint 1 (Week 1): Floating Hold/Lock Overlay & Service (`FR-01`, `FR-02`) — **Code Generated, Pending Device QA**
- [x] **S1-1:** Initialize multi-module Android Gradle project (`app`, `core:overlay`, `core:audio`) with Hilt and Jetpack Compose (`FieldFlow_Sprint1_Full.zip`).
- [x] **S1-2:** Implement permission onboarding (`RECORD_AUDIO`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`).
- [x] **S1-3:** Implement `OverlayCaptureService` (`ForegroundService` started from foreground + `WindowManager` `TYPE_APPLICATION_OVERLAY`) with `120 ms` hold, **Slide-Up to Lock**, `8dp` drag, 4 visual states, and `VibrationEffect`.
- [x] **S1-4:** Build 16 kHz mono `AudioRecord` capture loop with 2s pre-roll ring buffer and incremental `.m4a` `MediaMuxer` writer in `filesDir`.
- [ ] **S1-QA (Active Gate):** Install APK on a physical Android phone and verify hold, Slide-Up to Lock, edge snapping, and 10-minute screen-off recording before starting Sprint 2.

### Sprint 2 (Week 2): VAD & Live `.txt` File Watcher (`FR-03`, `FR-04`) — **Next Up**
- [ ] **S2-1:** Integrate **Silero VAD** (`onnxruntime-android`) to trim silence and split locked recordings into `SpeechSegment` chunks.
- [ ] **S2-2:** Compile and bind **whisper.cpp** via Android NDK/JNI (`tiny.en` / `base.en` quantized) with model-download manager.
- [ ] **S2-3:** Implement `WorkManager` transcription queue + atomic `TranscriptFileWatcher` writing to `filesDir` (plus optional `Documents/FieldFlow/` mirror).
- [ ] **S2-4:** Add **Auto-Copy to Clipboard** toggle on release.

### Sprint 3 (Week 3): Local Gemini Nano, FTS Search & AppFunctions (`FR-05`, `FR-07`, `FR-08`)
- [ ] **S3-1:** Implement Room entities + `@Fts4` full-text search + tap-to-seek audio playback.
- [ ] **S3-2:** Integrate **Gemini Nano** (`com.google.mlkit:genai-prompt`) for on-device NPU structured transforms (`Clean Dictation`, `Story Prose`, `Code & Specs`, `Work Log`, `Checklist`), with explicit opt-in cloud fallback.
- [ ] **S3-3:** Add `core:appfunctions` (`androidx.appfunctions:appfunctions` + KSP compiler) exposing `searchVoiceNotes` and `getLatestNote` with `BIND_APP_FUNCTION_SERVICE` and typed `AppFunctionException` errors.

### Sprint 4 (Week 4): Printouts, Share Sheet & Field QA (`FR-06`)
- [ ] **S4-1:** Implement `PrintManager` + `PdfDocument` printouts with clean print typography.
- [ ] **S4-2:** Implement `FileProvider` Share Sheet (`Intent.ACTION_SEND`) for `.txt`, `.md`, and `.pdf` (to Gmail, Drive, Docs, Keep, WhatsApp) + `geo:` Maps intent helper.
- [ ] **S4-3:** Add Quick Settings Tile to toggle the floating bubble on/off.
- [ ] **S4-4:** Execute full Test Matrix (Unit + Integration + 30-min physical device field QA).


---

## 10. Implementation Baseline Addendum (2026-11-26)

This addendum supersedes the historical sprint checkpoint above for repository-status purposes. The original sections remain the product intent and design baseline; they must not be interpreted as proof that every requirement is implemented or verified.

**Reviewed repository snapshot:** `main` at `e2517adda8203d5a24b2e1b9c48465ee9d44fab3`.  
**Review method:** Static source inspection. No Gradle build, emulator run, physical-device test, benchmark, or native inference run was performed for this documentation update.

The repository now contains source modules for overlay, audio capture, VAD, Whisper JNI, WorkManager transcription, transcript file writing, Room/FTS, Gemini Nano transforms, AppFunctions, export/print/share, and Quick Settings tile. Refer to `docs/IMPLEMENTATION_STATUS.md` for the source-evidence matrix.

### 10.1 Critical implementation clarifications

1. **Whisper JNI may be a stub.** `core/stt/src/main/cpp/CMakeLists.txt` builds a stub when the Whisper source tree is absent. The stub JNI implementation returns placeholder text. A release build must fail if real Whisper is not linked.
2. **Model assets are external prerequisites.** The worker expects `filesDir/models/silero_vad.onnx` and `filesDir/models/ggml-base.en-q5_1.bin`. The reviewed repository does not contain these assets or a complete provisioning manager.
3. **Two recording paths exist.** The overlay uses AudioRecord + WorkManager/VAD/Whisper. The in-app editor uses Android SpeechRecognizer with an AudioRecord-backed M4A capture. These paths have distinct transcription and persistence semantics; see ADR-0002.
4. **Build configuration is machine-specific.** App and AppFunctions Gradle files reference an absolute Android SDK 36.1 JAR path under `/home/machinerg/`. Replace this with a portable and CI-verifiable configuration.
5. **Recovery is not established.** The current source does not persist a complete recording-session journal before capture. WorkManager persistence does not by itself recover a recording that was not finalized and enqueued.
6. **Worker terminal states need correction.** Missing model assets and empty decode/segment paths can currently return success or placeholder output. A recording must not become `TRANSCRIBED` unless genuine transcript content is durably committed.
7. **Room schema evolution is not release-ready.** Database version is 1 and schema export is disabled. Explicit migrations and schema verification are required.
8. **AppFunctions returns transcript text.** Authorization by the platform binding permission does not replace product-level data minimization, user enablement, result limits, redaction and audit policy.
9. **Backup is disabled.** The manifest sets `allowBackup=false`; no app-level backup/restore flow was found.

### 10.2 Documentation source of truth

The production technical documentation is maintained under `docs/`:

- `docs/IMPLEMENTATION_STATUS.md`
- `docs/architecture/system-architecture.md`
- `docs/architecture/runtime-flows.md`
- `docs/data/data-model.md`
- `docs/security/security-privacy.md`
- `docs/engineering/build-release.md`
- `docs/quality/test-strategy.md`
- `docs/operations/operations-recovery.md`
- `docs/adr/`

The original sprint backlog is retained as historical planning context. Its checkbox state is not a current implementation-status ledger.

### 10.3 Production-readiness rule

A source implementation is not considered verified until there is reproducible evidence on the target Android version/device class. In particular, physical-device validation is required for microphone foreground-service behavior, overlay interaction, audio codec integrity, actual native Whisper inference, Gemini Nano availability, AppFunctions discovery/invocation, printing and sharing.

