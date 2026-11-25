# Technical Documentation

This directory is the source of truth for engineering documentation derived from the current `main` codebase. It separates current behavior from intended behavior and release requirements.

## Reading order

1. [Implementation Status](IMPLEMENTATION_STATUS.md) — what exists, what is incomplete, and what is not yet verified.
2. [System Architecture](architecture/system-architecture.md) — modules, boundaries, dependencies and runtime paths.
3. [Runtime Flows](architecture/runtime-flows.md) — overlay recording, transcription, editor capture, transformations and agent access.
4. [Data Model and Storage](data/data-model.md) — Room schema, file layout, ownership and consistency risks.
5. [Security and Privacy](security/security-privacy.md) — permissions, trust boundaries, data exposure and threat register.
6. [Build and Release](engineering/build-release.md) — toolchain, SDK constraints, native dependencies and release blockers.
7. [Test Strategy](quality/test-strategy.md) — existing automated coverage and required verification.
8. [Operations and Recovery](operations/operations-recovery.md) — model provisioning, failure behavior, diagnostics and recovery requirements.
9. [Architecture Decision Records](adr/README.md) — recorded decisions and decisions still requiring confirmation.

## Documentation rules

- **Implemented** means source code for the behavior exists. It does not mean it has been validated on a device.
- **Verified** means there is reproducible test evidence. The evidence and environment must be named.
- **Planned** means specified but not implemented.
- **Blocked** means a dependency or defect prevents the intended behavior.
- **Unknown** means the repository does not establish the behavior; do not fill the gap by assumption.
- Any implementation change that changes a contract must update the relevant document and tests in the same change.
- Never document a model, permission, API, backup policy, or recovery guarantee as available unless it is verified against the shipping artifact and target Android versions.

## Baseline

This documentation review is based on the `main` source snapshot at commit `e2517adda8203d5a24b2e1b9c48465ee9d44fab3`. It is a static source review. No Gradle build, emulator run, physical-device run, benchmark, or native inference test was executed as part of the review.
