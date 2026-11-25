# ADR-0003: Room metadata and app-private file artifacts

- **Status:** Accepted as current approach; hardening required
- **Date:** 2026-11-26
- **Context:** Audio, transcript and transformed document contents are file artifacts; queryable metadata and search index are relational data.
- **Decision:** Use Room for recording metadata, transcript segments, structured-document metadata and FTS4 rows. Store M4A, TXT, Markdown and PDF files under app-private `filesDir`. Optional public transcript mirroring uses MediaStore.
- **Consequences:** Room and filesystem writes are not one atomic transaction. A crash or retry can create missing/orphaned artifacts or duplicate transcript appends. Backup is disabled in the current manifest.
- **Required follow-up:** Add a durable session journal, idempotent segment processing, reconciliation, explicit migrations, deletion/retention and export/restore contracts.
