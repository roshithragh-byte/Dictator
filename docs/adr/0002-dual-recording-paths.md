# ADR-0002: Dual recording paths

- **Status:** Open — product decision required
- **Date:** 2026-11-26
- **Context:** The overlay path records PCM to M4A and schedules VAD/Whisper transcription. The in-app editor path uses Android SpeechRecognizer for English live text while separately recording M4A through the same audio controller.
- **Decision:** No final product decision is recorded. Documentation must describe the two paths separately and must not imply that they share the same transcription provider, offline guarantee, persistence behavior or failure handling.
- **Consequences:** Users may receive different results depending on where they start recording. The platform SpeechRecognizer path may have different network/offline behavior from local Whisper. Two capture controllers may be instantiated by different owners.
- **Options to evaluate:** (A) One shared capture and transcription pipeline; (B) retain both as explicit "Live OS recognition" and "Private offline transcription" modes; (C) remove one path.
- **Required decision:** Select a supported product behavior, permission/consent language, source-of-truth behavior and test contract before release.
