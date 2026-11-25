# Data Model and Storage

## 1. Storage model

The current implementation uses two persistence systems:

1. **Room database** (`fieldflow.db`) for recording metadata, transcript segments, structured-document metadata and FTS search rows.
2. **App-private filesystem** (`context.filesDir`) for original M4A audio, canonical TXT transcripts, transformed Markdown files and PDF exports.

The app manifest sets `android:allowBackup="false"`. This prevents assuming Android cloud backup is available. No app-level backup/restore implementation is present in the reviewed source.

## 2. Current Room entities

### `folder_slots` — `FolderSlotEntity`

| Field | Kotlin type | Notes |
|---|---|---|
| `id` | String | Primary key |
| `slotCode` | String | Comment indicates A–D |
| `name` | String | Folder display name |
| `defaultTemplate` | String | Default transform template |

The current DAO does not expose folder CRUD or seed operations in the reviewed source.

### `recordings` — `RecordingEntity`

| Field | Kotlin type | Notes |
|---|---|---|
| `id` | String | Primary key; generated UUID in current capture path |
| `folderId` | String | Folder identifier; no observed FK |
| `title` | String | Display title |
| `audioPath` | String | Absolute app-private audio path |
| `txtPath` | String | Absolute transcript path |
| `durationMs` | Long | Duration |
| `createdAtEpochMs` | Long | Epoch milliseconds |
| `status` | String | Comment lists RECORDING, QUEUED, TRANSCRIBED, ERROR |

Status is a free-form String today. The code currently writes `TRANSCRIBED` or `ERROR` in the worker; the documented full lifecycle is not yet implemented.

### `transcript_segments` — `TranscriptSegmentEntity`

| Field | Kotlin type | Notes |
|---|---|---|
| `id` | String | Primary key |
| `recordingId` | String | Recording reference; no observed FK |
| `segmentIndex` | Int | Segment order |
| `startMs` | Long | Segment start offset |
| `endMs` | Long | Segment end offset |
| `rawText` | String | Recognized text |

The SDD table previously mentioned confidence, but the current entity does not contain a confidence field.

### `structured_docs` — `StructuredDocEntity`

| Field | Kotlin type | Notes |
|---|---|---|
| `id` | String | Primary key; current repository uses recording ID + template |
| `recordingId` | String | Recording reference |
| `templateType` | String | Template enum name |
| `markdownContent` | String | Stored Markdown body |
| `mdPath` | String | Absolute Markdown path |
| `updatedAtEpochMs` | Long | Epoch milliseconds |

### `recording_fts` — `RecordingFtsEntity`

Room FTS4 entity with `recordingId`, `title`, `rawText`, and `structuredText`. The DAO joins this virtual table to `recordings` for search.

## 3. Current DAO operations

- Observe all recordings, newest first.
- Observe recordings by folder, newest first.
- Get latest recording.
- Get recording by ID.
- Get transcript segments for a recording.
- Get structured docs for a recording.
- Search recording FTS rows using a supplied MATCH expression.
- Upsert recording, FTS row and structured doc.
- Insert/replace transcript segments.

No delete, cascade, explicit transaction, folder management, migration, or recovery query is present in the reviewed DAO.

## 4. Current file layout

Observed path conventions:

```text
<filesDir>/
  recordings/
    <folderId>/
      <timestamp>_<recordingUuid>.m4a
      <timestamp>_<recordingUuid>.txt
      <timestamp>_<recordingUuid>_<template>.md
    exports/
      <sanitizedTitle>.pdf
  models/
    silero_vad.onnx
    ggml-base.en-q5_1.bin
```

The two model paths are expected by source code; the model assets are not present in the repository tree reviewed.

The optional public mirror is written to MediaStore under `Documents/FieldFlow/<folderId>/`. The current worker passes `mirrorToPublicDocuments=false` by default.

## 5. Current consistency behavior and risks

- Transcript append uses a temporary sibling file and rename attempt, then falls back to direct write if rename fails.
- Transcript file append and Room updates are separate operations.
- Worker retries can repeat a transcript append because the writer does not accept an idempotency key or segment checkpoint.
- File writer operations are not serialized per recording in the current implementation.
- Repository file writes use direct `writeText` calls for named notes and Markdown.
- The database schema is version 1 and `exportSchema=false`.
- There are no observed foreign keys or explicit indexes for folder/recording relationships.
- A recording metadata row is generally written after worker processing, not at capture start.
- The worker can persist a placeholder transcript while marking the recording `TRANSCRIBED`.

## 6. Production target recommendations

These are recommended changes, not current schema claims:

1. Introduce a typed `RecordingStatus` enum with a single transition validator.
2. Persist a recording row before capture begins, including stable ID, path, state and timestamps.
3. Add explicit foreign keys and indexes for segment/doc relationships.
4. Export Room schemas and check them into version control; add tested migrations.
5. Add a processing checkpoint table or segment state to make worker retries idempotent.
6. Treat canonical audio and transcript files as immutable source artifacts; use atomic replace for edits and transformations.
7. Add explicit note deletion, retention, export/import and restore semantics.
8. Sanitize folder IDs and filenames; generate filesystem names from UUIDs rather than user input.
9. Define whether FTS indexes raw text, structured text or both; test query parsing and synchronization.
10. Add a reconciliation routine that detects orphaned files, orphaned rows, missing files and incomplete sessions.

## 7. Data classification

| Data | Sensitivity | Current storage |
|---|---|---|
| Raw audio | Potentially highly sensitive | App-private M4A |
| Raw transcript | Potentially highly sensitive | App-private TXT |
| Structured document | Same sensitivity as source unless classified otherwise | App-private Markdown + Room metadata |
| Search index | Derived sensitive content | Room FTS4 |
| Model binaries | Application assets | Expected under app-private models directory |
| PDF export | User-generated content | App-private exports directory, shared by URI |

Do not assume that app-private storage is equivalent to end-to-end encryption or a user-controlled backup. Android file-based encryption is device/platform protection; the app does not currently implement an independent encryption/key-management layer.
