# AmniQuest v0.0.1 (Amni Ecosystem)

> [!NOTE]
> **Project Heritage & Rebuild**: AmniQuest is a fresh rebuild, complete redesign, and fork of [QuestPhone](https://github.com/QuestPhone/questphone) originally created by **Nethical**. It is maintained under **Al-Haq Studio** as part of the **Amni Ecosystem**, with versioning restarting fresh from **v0.0.1**, focusing on privacy, offline-first execution, on-device AI verification, and mindful screen time.

**AmniQuest** is an open-source Android productivity utility and minimal launcher designed to help users combat screen addiction by gamifying habit-forming and app-blocking workflows. Distracting apps remain blocked until you successfully complete your real-life quests.

---

## Key Features

- **Minimalist Launcher**: Minimizes screen clutter and keeps your focus where it belongs.
- **Gamified Habits**: Level up, build streaks, earn coins, collect objects, and track progress.
- **Strict Real-Life Verification**: Uses hardware-accelerated local AI or external integrations to verify that you actually completed your quests.
- **On-Device Privacy First**: Local-first architecture with zero mandatory account requirements.
- **AES-256 Backup Encryption**: Export and import your data with high-grade PBKDF2 password-derived AES-256-CBC encryption.
- **Habit Privacy Mode**: Hide sensitive quest titles (e.g. medical or personal habits) behind a masked state, unlocked via Android Biometrics (Fingerprint/Face/PIN/Pattern).
- **Background Auto-Backups**: Periodically saves local rolling backups of your profile and quests in the background via Android `WorkManager`.
- **Custom Theme Suite**: 7 custom themes including Cyberpunk, Nordic Frost, Solarized Sunset, Cherry Blossoms, Hacker, Pitch Black, and Bonsai.
- **Home Screen Widgets**: Track active quests, daily streaks, and coins directly from your device launcher with native AppWidgets.
- **Sync Migrator**: Seamlessly uploads and merges offline anonymous statistics and quests to your online account upon registration or login.

---

## Screenshots

Click on any image to enlarge.

<table>
  <tr>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/1.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/2.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/3.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/4.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/5.png' width='120'></td>
    <td><img src='fastlane/metadata/android/en-US/images/phoneScreenshots/6.png' width='120'></td>
  </tr>
</table>

---

## Tech Stack & Architecture

> [!NOTE]
> For a comprehensive breakdown of the project architecture, multi-module structure, product flavors (`play` vs `fdroid`), app blocker foreground service, and AI validation pipelines, see [ARCHITECTURE.md](ARCHITECTURE.md).

- **UI & State**: Jetpack Compose, Material Design 3, Dagger Hilt.
- **Database & Sync**: Room (SQLite), Supabase Auth & Database Sync.
- **On-Device AI Engine**:
  - **Unified ViewModel**: Single `AiSnapQuestViewVM` in `app/src/main/` driving on-device AI verification across both Google Play and F-Droid distributions with 8 real-time progress steps.
  - **SigLIP 2 ONNX Suite**: 5 local vision models (`SIGLIP2_UINT8` 378 MB, `SIGLIP2_INT8` 378 MB, `SIGLIP2_Q4F16` 497 MB, `SIGLIP2_FP16` 751 MB, `SIGLIP2_FULL` 1500 MB) with multi-precision tensor extraction (`FLOAT`, `FLOAT16`, `BFLOAT16`, `DOUBLE`).
  - **SentencePiece C++ JNI**: Mutex-synchronized native wrapper compiled via CMake with Android 15+ 16 KB page alignment and Gemma tokenization specs (`PAD=0L`, `EOS=1L`, `maxLength=64`).
  - **Hardware Acceleration & Fault Recovery**: NNAPI hardware execution with automatic seamless CPU fallback upon runtime errors.
  - **System Gemini Nano**: Google `AICore` prompt reasoning with robust JSON parsing and calibrated SigLIP 2 zero-shot fallback.
  - **Resilient Background Downloads**: `FileDownloadWorker` supporting HTTP 206 Partial Content Range resume, dedicated tokenizer queue, non-expedited fallback, and offline catalog fallback in `ModelDownloadDialog`.
  - **Fallback Chain**: Local SigLIP 2 ONNX / System Gemini Nano -> Calibrated SigLIP 2 Zero-Shot -> Private Gemini API Key -> Cloud Server.

---

## Build & Development Instructions

### Prerequisites
1. **JDK 17** (OpenJDK 17 recommended)
2. **Android SDK** (API 36 / 34)
3. **NDK & CMake** (NDK 25.2.9519653, CMake 3.22.1+)

### Build Commands

To run tests and compile both build variants:

```powershell
# Run the automated test suite across all modules
.\gradlew.bat test

# Compile Kotlin and generate debug APKs
.\gradlew.bat --gradle-user-home D:\PROJECTS\Amni\AmniQuest\.gradle_home assemblePlayDebug assembleFdroidDebug

# Generate release Android App Bundle (AAB)
.\gradlew.bat --gradle-user-home D:\PROJECTS\Amni\AmniQuest\.gradle_home bundlePlayRelease
```

### Build Notes

> [!TIP]
> **Windows Gradle Multi-Drive Root Limitation**:
> If your project resides on a secondary drive (e.g. `D:`) while the default Gradle cache folder is on `C:`, KSP may encounter path resolution issues. Bypass this by setting the Gradle user home directory on the same drive:
> ```powershell
> .\gradlew.bat --gradle-user-home D:\PROJECTS\Amni\AmniQuest\.gradle_home assemblePlayDebug assembleFdroidDebug
> ```

> [!NOTE]
> **Android 15+ 16 KB Memory Page Alignment**:
> [CMakeLists.txt](ai/src/main/cpp/CMakeLists.txt) configures `-Wl,-z,max-page-size=16384` for native shared libraries, and `app/build.gradle.kts` sets `useLegacyPackaging = false` to guarantee compatibility with Android 15+ 16 KB page size devices.

---

## Roadmap

- **v0.1.0**: Advanced Model Profiling & NPU Optimization (runtime benchmark metrics and SoC auto-tuning).
- **v0.2.0**: Advanced Interactive Home Screen AppWidgets.
- **v0.3.0**: Wear OS Companion App support.
- **v0.4.0**: Optional Peer Accountability & Encrypted Challenges via Supabase.

For detailed project milestones and architecture, see [PROJECT.md](PROJECT.md) and [ARCHITECTURE.md](ARCHITECTURE.md).

---

## License

Licensed under the [Apache License 2.0](LICENSE).

