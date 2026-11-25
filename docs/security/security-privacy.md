# Security and Privacy

## 1. Security objectives

Dictator / FieldFlow handles microphone recordings and transcripts that may contain personal, business, health, legal or other sensitive information. The security model must therefore prioritize:

- Explicit microphone and overlay consent.
- Local processing by default.
- Least-privilege component exposure.
- Controlled disclosure to system/AI agents.
- Safe sharing/export behavior.
- Reliable deletion and user control over retained content.

This document describes observed controls and remaining requirements; it is not a security certification.

## 2. Manifest permissions and components

| Manifest declaration | Purpose | Current observation |
|---|---|---|
| `RECORD_AUDIO` | Microphone capture | Runtime permission required |
| `SYSTEM_ALERT_WINDOW` | Floating overlay | Special app-op/settings grant, not a normal runtime permission |
| `FOREGROUND_SERVICE` | Foreground service | Declared |
| `FOREGROUND_SERVICE_MICROPHONE` | Microphone FGS type | Declared |
| `POST_NOTIFICATIONS` | Notification visibility | Declared; current permission gate checks overlay and microphone, not notification permission |
| `WAKE_LOCK` | CPU wake lock | Declared; observed controller does not acquire a wake lock |
| `VIBRATE` | Haptic feedback | Declared |

Component exposure:

- `MainActivity` is exported as launcher activity.
- `OverlayCaptureService` is not exported.
- `FieldFlowTileService` is exported but protected by `BIND_QUICK_SETTINGS_TILE`.
- AppFunctions service is exported and protected by `BIND_APP_FUNCTION_SERVICE`.
- FileProvider is not exported and grants URI permissions.

## 3. Data handling

### Local storage

The manifest disables Android backup. Audio, transcript, Markdown and PDF files are written under app-private `filesDir`. This limits ordinary cross-app filesystem access but is not a substitute for application-level encryption or an explicit retention policy.

### Network/cloud processing

The architecture intends on-device Whisper and Gemini Nano by default. The current transformer calls ML Kit GenAI Prompt API and falls back to local formatting. No cloud fallback client was found in the reviewed transform implementation. Android SpeechRecognizer is a separate platform service path; its processing location and offline availability are not controlled by this app implementation.

The current manifest does not declare an INTERNET permission. Do not document cloud fallback as implemented.

### Public mirror

The transcript watcher supports optional MediaStore mirroring to `Documents/FieldFlow`. The worker defaults this flag to false. A user-facing persisted preference and clear consent UX were not established by the reviewed source.

## 4. AppFunctions trust boundary

The AppFunctions service is exported and protected with `android.permission.BIND_APP_FUNCTION_SERVICE`. It exposes:
- `searchVoiceNotes(query)`
- `getLatestNote()`

Both operations can return full transcript text in `VoiceNoteSummary`. This is a sensitive disclosure surface even though the service uses the platform binding permission.

Before release, define and test:
1. Which platform components/agents can invoke the functions on each supported OS.
2. Whether the user must enable the capability or approve each disclosure.
3. Whether only selected folders/notes may be exposed.
4. Maximum result count, transcript size and response time.
5. Redaction policy for secrets and personal data.
6. Whether raw audio paths or file URIs can ever be returned (they should not be returned by default).
7. Access audit events and user-visible controls.
8. Behavior when AppFunctions is unavailable or disabled.

Never treat the binder permission as a complete user-consent or data-minimization policy.

## 5. Input and filesystem safety

The current repository uses folder IDs and note titles in filesystem paths. Before production:
- Restrict folder identifiers to an allowlisted identifier format.
- Generate filenames from stable UUIDs; keep display titles as database values.
- Reject path separators, traversal sequences, control characters and reserved names.
- Enforce maximum lengths for user text, title and search query.
- Handle FTS operators and malformed MATCH expressions without crashing.
- Treat transcript content as untrusted data when inserted into prompts or rendered for export.
- Continue HTML-escaping all user-provided text in WebView print content.

## 6. Threat register

| Threat | Impact | Existing mitigation | Required mitigation |
|---|---|---|---|
| Unauthorized microphone capture | Privacy violation | Runtime RECORD_AUDIO; foreground microphone service | Clear active indicator, start/stop controls, API/OEM tests |
| Overlay abuse / deceptive UI | User confusion or tapjacking | User-granted overlay permission; non-focusable window | UX disclosure, no deceptive overlays, lifecycle and abuse review |
| Transcript disclosure to agent | Sensitive data exposure | BIND_APP_FUNCTION_SERVICE | Explicit opt-in, scopes, result limits, redaction and audit |
| Shared file URI misuse | Data disclosure | Non-exported FileProvider and URI grant | Restrict paths/MIME types, test revocation and recipient behavior |
| Path traversal | Read/write outside intended directory | App-private base directory | Strict identifier and filename validation |
| Malformed FTS query | Search failure/DoS | Query sanitization removes double quotes and adds wildcard | Proper tokenization, bounds and error handling |
| Model supply-chain compromise | Native code/model execution or corrupted inference | No model integrity policy observed | Pin upstream revision, verify hashes/signatures, document licenses |
| Device loss / app data loss | Irrecoverable recordings | App-private storage; backup disabled | Explicit backup/export/restore policy |
| Log leakage | Sensitive transcript exposure | No transcript logging observed in reviewed files | Define no-content logging rule and inspect release logs |

## 7. Privacy requirements for release

- No transcript/audio content in analytics, crash reports or logs by default.
- No network transmission of audio/transcript without explicit, revocable user opt-in.
- Explain SpeechRecognizer's platform processing separately from on-device Whisper.
- Provide note-level delete and full local-data deletion.
- Explain backup-disabled behavior and provide export/restore strategy if required.
- Make public mirroring an explicit, persistent preference with clear destination.
- AppFunctions must return only the minimum content needed for the invoking task.
- Document retention and temporary-file cleanup.
- Verify exported APK manifest and AppFunctions metadata, not only source declarations.
