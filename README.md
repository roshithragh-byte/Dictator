# Dictator / FieldFlow

Android-first, voice-centric note capture application. The current implementation combines a floating recording overlay, local audio capture, an offline transcription pipeline, a Compose note editor, on-device text transformation, Android AppFunctions, and document export.

> **Engineering status:** This repository is an active implementation, not yet a production-readiness declaration. Some paths are implemented but depend on external model assets or native libraries; others need device-level validation. See [Implementation Status](docs/IMPLEMENTATION_STATUS.md) before treating a capability as release-ready.

## Project structure

- `app` — Android application, Compose UI, permission onboarding and dependency injection.
- `core:overlay` — floating overlay, microphone foreground service and Quick Settings tile.
- `core:audio` — 16 kHz mono PCM capture, pre-roll buffer and AAC/M4A encoding.
- `core:stt` — VAD/transcription worker and Whisper JNI boundary.
- `core:watcher` — canonical transcript file writing and optional MediaStore mirror.
- `core:data` — Room entities, DAO, database and repository.
- `core:transform` — Gemini Nano prompt transformations and deterministic fallback templates.
- `core:appfunctions` — Android AppFunctions for note search and latest-note retrieval.
- `core:export` — print, PDF export, share sheet and Maps intent.

## Technical documentation

Start with [Documentation Index](docs/README.md). The documentation distinguishes observed implementation from intended behavior, assumptions and unresolved production gates.

## Build baseline

The checked-in Gradle configuration currently declares:

- Android Gradle Plugin 8.7.2
- Kotlin 2.0.21
- Java/JVM target 17
- App minSdk 30, targetSdk 35, compileSdk 35
- Android AppFunctions APIs are compiled against a machine-local Android 36.1 SDK JAR in the current app/module Gradle files.

The local SDK JAR reference is not portable and is tracked as a P0 build/release issue. See [Build and Release](docs/engineering/build-release.md).

A successful build, install, model-backed inference run, and physical-device test have **not** been established by this documentation review. Do not infer those results from source presence.

## Product naming

The codebase uses both **Dictator** (application label/UI) and **FieldFlow** (package/module/service naming and original SDD). Treat this as an unresolved product identity decision until naming is standardized.
