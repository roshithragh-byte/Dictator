# CEO Plan: Play Protect Resolution, App Trust & Streamlined Onboarding for Dictator

## 1. Executive Summary & Problem Diagnosis
When installing `dictator_debug.apk`, modern Android devices running Google Play Protect (specifically Android 13/14/15+) trigger an aggressive security shield:
> *"App blocked to protect your device. This app can request access to sensitive data. This can increase the risk of identity theft or financial fraud."*

### Why Play Protect Triggers This Specific Warning:
1. **Unscoped Accessibility Service (`canRetrieveWindowContent="true"` without package filters)**:
   In `app/src/main/res/xml/accessibility_service_config.xml`, the accessibility service declares global content retrieval across all installed apps on the device without specifying `android:packageNames`. Play Protect classifies unverified global accessibility services that inspect windows as high-risk keystroke/credential harvesters.
2. **Debug Certificate Signature**:
   `dictator_debug.apk` is signed by the generic, untrusted Android SDK `debug.keystore`. Sideloaded APKs signed with debug keys declaring elevated services trigger immediate heuristics blocks.
3. **Restricted Settings on Android 13+ (Sideload Protection)**:
   Even if the user bypasses Play Protect, Android 13+ marks sideloaded accessibility apps as "Restricted settings" until unlocked manually in App Info.

---

## 2. Strategic Objectives & The 10x Experience
To make Dictator install seamlessly, establish instant trust, and deliver high user satisfaction:

1. **Eliminate Malicious Heuristics at the Manifest & Service Level**:
   - Scope the Accessibility Service explicitly in `accessibility_service_config.xml` to `packageNames="com.instagram.android,com.google.android.youtube,com.facebook.katana,com.zhiliaoapp.musically,com.snapchat.android,com.reddit.frontpage,com.twitter.android"`.
   - By scoping package names, the OS knows the service physically *cannot* read bank apps, messaging apps, or passwords.
   - Remove redundant broad event types (`typeWindowContentChanged` throttle).
2. **Production Release Keystore for Distribution**:
   - Sign public release builds with a dedicated Dictator release certificate rather than the generic Android debug key.
3. **Flawless In-App Onboarding & Restricted Settings Walkthrough**:
   - For Android 13+, build a step-by-step visual onboarding guide directly in `MainActivity`:
     - Step 1: Explain *why* Dictator needs overlay & accessibility (showing exact zero-data-collection pledge).
     - Step 2: If accessibility is blocked by "Restricted setting", provide a 1-tap intent directly to App Settings with visual cues on how to allow restricted settings.

---

## 3. Architecture & Implementation Steps

```
[User Downloads Dictator Release APK]
        │
        ▼
[Play Protect Checks Manifest]
        │
        ├──> Declares packageNames whitelist (Instagram, YouTube, etc.)
        ├──> Signed with dedicated Release Keystore
        └──> Minimal permissions footprint
        │
        ▼
[Installation Succeeds]
        │
        ▼
[MainActivity Launch: Trust & Permission Engine]
        │
        ├──> 1. Explains Privacy Commitment (No keystrokes, no network calls)
        ├──> 2. Requests "Display over other apps"
        └──> 3. Guides user to Accessibility Service activation
```

### Detailed Changes:
1. **`accessibility_service_config.xml`**:
   - Add `android:packageNames="com.instagram.android,com.google.android.youtube,com.facebook.katana,com.zhiliaoapp.musically,com.ss.android.ugc.trill,com.snapchat.android,com.reddit.frontpage,com.twitter.android"`
   - Increase `android:notificationTimeout="100"` to reduce system overhead and battery impact.
2. **Keystore Generation**:
   - Create a dedicated `dictator-release.jks` and configure `signingConfigs` in `app/build.gradle.kts`.
3. **Build `dictator_release.apk`**:
   - Automated via `./gradlew assembleRelease`.
4. **GitHub Release v1.0.1**:
   - Tag and release `dictator_release.apk` alongside clear instructions.

---

## 4. Immediate Sideload Bypass Guide (for Current Testing)
Until v1.0.1 with scoped packages and release signing is built, users on current test builds can bypass the prompt:
- **Option A**: In Google Play Store $\rightarrow$ Profile $\rightarrow$ Play Protect $\rightarrow$ Settings gear $\rightarrow$ Temporarily turn off "Scan apps with Play Protect".
- **Option B**: Connect via USB / ADB: `adb install -r -d dictator_debug.apk`.
