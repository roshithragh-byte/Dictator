# ADR-0004: Android AppFunctions for voice-note retrieval

- **Status:** Implemented surface; privacy and platform support policy open
- **Date:** 2026-11-26
- **Context:** The product wants compatible system AI experiences to query locally stored voice notes without requiring a remote service.
- **Decision:** Expose two operations through AndroidX AppFunctions: `searchVoiceNotes(query)` and `getLatestNote()`. The current response includes note identity, title, folder, full transcript text and creation timestamp.
- **Consequences:** This is a privileged data-disclosure boundary. Full transcript content may be returned to the invoking system/agent. Platform support is API-dependent; the service class is annotated for API 36.
- **Required follow-up:** Define user enablement/consent, caller expectations, data scopes, response limits, redaction, audit events, and behavior on unsupported OS versions. Test generated service metadata and invocation on a physical API 36+ device.
