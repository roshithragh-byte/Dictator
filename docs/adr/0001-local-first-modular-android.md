# ADR-0001: Local-first modular Android architecture

- **Status:** Accepted as current design
- **Date:** 2026-11-26
- **Context:** The product is an Android voice-capture and note-processing application. Recording and primary note access should not depend on a remote backend.
- **Decision:** Use a Kotlin Android application split into `app` and focused `core:* ` modules. Keep audio capture and core transcription local; use Android platform APIs for foreground service, overlay, WorkManager, Room, ML Kit GenAI, AppFunctions and document sharing.
- **Consequences:** Core functionality can work without an application backend. Android OS version, device hardware, native ABI and model availability become key compatibility variables. Module boundaries need contract tests and explicit dependency ownership.
- **Follow-up:** Make build configuration portable, pin native/model dependencies, and prove local inference on physical devices.
