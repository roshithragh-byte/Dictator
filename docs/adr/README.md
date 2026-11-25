# Architecture Decision Records

ADRs record why an architectural choice exists, its consequences and whether it remains valid.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-local-first-modular-android.md) | Local-first multi-module Android architecture | Accepted as current design |
| [0002](0002-dual-recording-paths.md) | Separate overlay/Whisper and editor/SpeechRecognizer paths | Temporary implementation state; product decision required |
| [0003](0003-room-and-filesystem-storage.md) | Room metadata plus app-private file artifacts | Accepted as current storage approach; consistency work required |
| [0004](0004-appfunctions-data-boundary.md) | Expose note search/latest note through Android AppFunctions | Implemented surface; privacy policy incomplete |

New decisions should be added as numbered files. Do not silently change an accepted decision; supersede it with a new ADR.
