# AmniQuest (AmniSpace)

> [!NOTE]
> **Project Heritage**: AmniQuest is an open-source, modernized focus launcher and quest utility developed under **Al-Haq Studio** as part of the **Amn Ecosystem**. It is maintained with an emphasis on privacy, offline-first execution, on-device AI verification, high performance, and mindful screen time.

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
- **Custom Theme Suite**: Custom themes including Cyberpunk, Nordic Frost, Solarized Sunset, Cherry Blossoms, Hacker, and Pitch Black.
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

- **UI & State**: Jetpack Compose, Material Design 3, Dagger Hilt.
- **Database & Sync**: Room (SQLite), Supabase Auth & Database Sync.
- **On-Device AI Engine**:
  - **SentencePiece JNI**: Custom JNI native C++ wrapper compiled via CMake.
  - **SigLIP ONNX Runtime**: Local zero-shot vision classification for image features.
  - **Gemini Nano (AICore)**: Local LLM reasoning utilizing Google's system-level `AICore` client API.
  - **Fallback Chain**: Local Gemini Nano -> Private Gemini API Key -> Cloud Server Validation -> Local SigLIP.

---

## Build & Development Instructions

### Prerequisites
1. **JDK 17** (OpenJDK 17 recommended)
2. **Android SDK** (API 36 / 34)
3. **NDK & CMake** (Install via Android Studio SDK Manager)

### Build Commands

To build and compile both build variants:

```powershell
# Compile Kotlin and generate release APKs
.\gradlew.bat --gradle-user-home D:\PROJECTS\QuestPhone\.gradle_home assemblePlayDebug assembleFdroidDebug
```

### Build Notes

> [!TIP]
> **Windows Gradle Multi-Drive Root Limitation**:
> If your project resides on a secondary drive (e.g. `D:`) while the default Gradle cache folder is on `C:`, KSP may encounter path resolution issues. Bypass this by setting the Gradle user home directory on the same drive:
> ```powershell
> .\gradlew.bat --gradle-user-home D:\PROJECTS\QuestPhone\.gradle_home assemblePlayDebug assembleFdroidDebug
> ```

> [!NOTE]
> **JNI Configuration on Windows**:
> [CMakeLists.txt](file:///d:/PROJECTS/QuestPhone/ai/src/main/cpp/CMakeLists.txt) references `src/` and `src/builtin_pb/` explicitly to ensure correct native builds on Windows.

---

## License

Licensed under the [Apache License 2.0](LICENSE).

