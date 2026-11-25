# Build and Release

## 1. Checked-in toolchain baseline

| Setting | Current repository value |
|---|---|
| Android Gradle Plugin | 8.7.2 |
| Kotlin | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| Java source/target | 17 |
| App compileSdk | 35 |
| App targetSdk | 35 |
| App minSdk | 30 |
| App versionCode | 1 |
| App versionName | 1.0.0-v1 |
| NDK ABI filters in STT module | arm64-v8a, armeabi-v7a |
| WorkManager | 2.10.0 |
| Room | 2.6.1 |
| ONNX Runtime | 1.20.0 |
| ML Kit GenAI Prompt | 1.0.0-beta4 |
| AndroidX AppFunctions | 1.0.0-rc01 |

Versions are read from `gradle/libs.versions.toml` and module Gradle files. Revalidate dependency compatibility and supported versions before release.

## 2. Build blocker: local Android platform JAR

The following Gradle files include an absolute local compile-only path:

- `app/build.gradle.kts`
- `core/appfunctions/build.gradle.kts`

Current path: `/home/machinerg/Android/Sdk/platforms/android-36.1/android.jar`.

This makes the build dependent on one developer machine and bypasses a reproducible SDK declaration. Remove the absolute file dependency and configure a supported compile SDK / API dependency arrangement for AppFunctions. CI and a clean developer machine must build without local path edits.

## 3. Native Whisper build

`core/stt/src/main/cpp/CMakeLists.txt` checks for a Whisper source directory at `core/stt/src/main/cpp/whisper.cpp` (or `WHISPER_CPP_DIR`). If absent, it prints a warning and builds a stub JNI library.

The JNI stub can load a dummy pointer and returns placeholder text from `nativeTranscribe`. This must not be permitted in release artifacts.

Release requirements:
- Pin whisper.cpp to a reviewed upstream tag/commit or submodule revision.
- Record license and redistribution obligations.
- Build for each supported ABI.
- Fail the release build if the real Whisper target is not linked.
- Add a native smoke test that loads the selected model and transcribes a known fixture.
- Verify model compatibility and integrity.
- Publish model acquisition instructions and checksums.

## 4. Model assets

Source code expects:
- `filesDir/models/silero_vad.onnx`
- `filesDir/models/ggml-base.en-q5_1.bin`

These files are not present in the inspected repository tree. No complete model download/import manager was found. The product must decide whether models are provisioned by download, user import or bundled delivery. Account for storage size, checksum validation, version upgrades, deletion, licensing and low-storage handling.

## 5. Local build commands

The expected Gradle entry point is the wrapper:

```bash
./gradlew --version
./gradlew projects
./gradlew test
./gradlew assembleDebug
```

These commands are a recommended verification sequence, not evidence that the current source passes. Run from a clean checkout with the documented Android SDK, NDK and CMake installed.

For release validation, additionally run:

```bash
./gradlew lint
./gradlew test
./gradlew assembleRelease
```

Do not publish an APK until the real native STT dependency and model assets are present and the release artifact is inspected.

## 6. Release engineering requirements

- Use a clean CI runner with pinned JDK, Gradle wrapper, Android SDK, NDK and CMake.
- Do not commit keystores, passwords, signing properties or local SDK paths.
- Load signing material from protected CI secrets or a secure local release process.
- Produce reproducible build metadata: commit SHA, versionCode/versionName, build variant, toolchain versions and native dependency revision.
- Verify APK/AAB signature, package ID, manifest, exported components, permissions and ABI contents.
- Generate SBOM/dependency inventory and track native/model licenses.
- Run static analysis and dependency vulnerability checks.
- Retain release artifacts and checksums.
- Increment versionCode for every distributed build.
- Document rollback and staged rollout process.

## 7. Release gate

A release candidate is blocked until:
1. Clean checkout build succeeds without machine-local paths.
2. Release variant cannot compile the Whisper stub.
3. Real inference works on each supported ABI/device class.
4. Model assets are provisioned and integrity-checked.
5. API-level permission/foreground-service/AppFunctions tests pass.
6. Data migration, export and deletion behavior are defined.
7. Security and privacy release checks pass.
8. Physical-device QA and battery/thermal testing are recorded.
