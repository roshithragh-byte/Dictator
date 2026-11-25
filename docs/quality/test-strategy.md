# Test Strategy

## 1. Testing principles

The app depends on Android system services, audio codecs, native inference, Room, WorkManager and on-device ML. JVM unit tests alone cannot establish production readiness.

Testing must be layered:
- Pure JVM unit tests for deterministic logic.
- Android instrumentation tests for platform APIs, database and files.
- Native tests for JNI and model execution.
- End-to-end tests on physical devices across supported API levels and OEM variants.
- Release-artifact verification for manifest, signing, ABI and model packaging.

## 2. Existing checked-in tests

| Test file | Current scope |
|---|---|
| `core/audio/.../AudioCaptureControllerTest.kt` | Audio capture/ring buffer related unit tests |
| `core/audio/.../SileroVadSegmenterTest.kt` | VAD segmenter tests |
| `core/overlay/.../OverlayStateMachineTest.kt` | Gesture threshold/state enum assertions |
| `core/transform/.../DocumentTransformerTest.kt` | Empty input and template output checks |
| `core/appfunctions/.../FieldFlowAppFunctionsTest.kt` | AppFunctions operations behavior |

The existence of these tests does not establish that they pass in CI. The overlay tests currently assert threshold arithmetic and enum values; they do not drive the actual Android touch listener with MotionEvents.

## 3. Required test suites

### Audio capture
- Ring buffer: empty, partial, exactly full, wraparound, multiple wraps, drain/reset, concurrent writes.
- AudioRecord initialization failure and permission denial.
- Codec initialization failure, encoder input/output backpressure, EOS finalization.
- Stop immediately after start; repeated start/stop; service destroy during capture.
- Disk full and file write failure.
- Validate M4A metadata, sample rate, channel count, duration and playable output.

### Overlay and service lifecycle
- Tap below 120 ms; hold at/above 120 ms; movement at and around 8dp; upward lock at and around 48dp.
- ACTION_CANCEL and pointer interruption.
- Drag while recording; touch outside bubble; rotation/display size changes.
- Overlay permission revoked while service is running.
- Service start from foreground/background, notification permission denied, process recreation and force-stop behavior.
- API 30, 33, 34, 35 and current supported platform versions.
- Test screen-off and major OEM battery-management behavior.

### VAD and native STT
- Silero model present, missing, invalid and incompatible.
- Speech/noise/silence fixtures; boundary frames; trailing speech without silence; multi-minute input.
- Real Whisper model load, known English fixture, empty/invalid PCM, cancellation and repeated load/release.
- JNI error handling, native crash and ABI smoke tests.
- Assert no placeholder output can be persisted as a valid transcript.
- Record WER/CER, real-time factor, peak memory, CPU and thermal measurements.

### WorkManager
- Unique work deduplication.
- Worker process death and retry.
- Missing audio/model; corrupt audio; empty decode; JNI failure.
- Cancellation during VAD and transcription.
- Retry idempotency: no duplicate transcript lines or segments.
- Verify terminal recording status for every exit path.

### Room and file consistency
- DAO and FTS integration tests on actual SQLite/Room.
- Schema export and migration tests from every supported prior version.
- File write interrupted before/after rename.
- DB update failure after file commit and vice versa.
- Concurrent segment writes, duplicate worker invocation and reconciliation.
- Missing transcript/audio file and orphaned DB row behavior.
- FTS punctuation, quotes, wildcard, Unicode, blank and malicious query cases.

### Transform and AppFunctions
- Gemini Nano unavailable, downloading, ready, unsupported and generation failure.
- Verify fallback is labeled as fallback; never claim AI transformation occurred when it did not.
- Output length limits, prompt injection in transcript text and cancellation.
- AppFunctions invocation on supported Android version; unavailable API fallback.
- Caller authorization, disabled function, blank query, very large query, result cap, large transcript and missing file.
- Ensure no audio path or unrequested sensitive fields are returned.

### Export and sharing
- Empty, very long and Unicode documents.
- Multi-page PDF boundaries and line wrapping.
- Concurrent export with same title; collision avoidance.
- FileProvider path restrictions and read permission grant.
- Print WebView lifecycle when Activity is destroyed.
- Share to representative targets and verify MIME type/URI readability.

## 4. Device matrix

At minimum, select representative devices for:
- API 30–33 (legacy supported range).
- API 34–35 (microphone foreground-service restrictions).
- API 36+ (AppFunctions path).
- At least one low-memory device and one current mid/high-tier device.
- At least two OEM power-management implementations.

The exact supported API range must be decided and reflected consistently in minSdk, compileSdk, targetSdk, AppFunctions gating and documentation.

## 5. Performance and reliability budgets to define

Do not invent thresholds after testing. Product/engineering must set budgets for:
- Time from touch gesture to audible/visible recording confirmation.
- Audio capture dropouts and maximum supported session length.
- Transcription real-time factor and maximum queue latency.
- Peak RAM for capture, VAD and Whisper.
- Battery drain per 30 minutes of recording.
- Thermal throttling behavior.
- Maximum local storage consumed by recordings/models.
- AppFunctions response latency and maximum payload.
- PDF export time and maximum document size.

## 6. CI expectations

Every pull request should run:
- Gradle configuration and module compilation.
- JVM unit tests.
- Lint/static analysis.
- Native build for configured ABIs with stub detection.
- Room schema/migration checks.
- Dependency/license inventory.

Nightly or device-lab jobs should run instrumentation, audio fixture, AppFunctions, end-to-end and performance suites. Physical-device release QA must remain a separate recorded gate.

## 7. Test evidence template

For every release gate, record:

| Field | Required value |
|---|---|
| Commit SHA | Exact tested revision |
| Build variant | Debug/release |
| Artifact SHA-256 | Exact APK/AAB |
| Device | Manufacturer/model |
| Android version/API | Exact OS build/API |
| Model assets | Names, versions and checksums |
| Test procedure | Reproducible steps |
| Result | Pass/fail/blocked |
| Logs/artifacts | Sanitized references |
| Known deviations | Explicit list |
