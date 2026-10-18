# AmniQuest v0.0.1 Architecture, Blueprint & Technical Logic Specification

## Executive Overview
**AmniQuest v0.0.1** (developed under Al-Haq Studio as part of the Amn Ecosystem) is a fresh rebuild, complete redesign, and fork of [QuestPhone](https://github.com/QuestPhone/questphone) originally created by **Nethical**. It is an open-source, privacy-first Android minimal launcher and habit gamification framework designed to combat screen addiction by locking distracting applications behind real-life quests verified by on-device AI or external integration systems. Versioning starts fresh at **v0.0.1**.

---

## 1. Multi-Module Project Blueprint

The project follows a modular Clean Architecture structure using Gradle multi-project builds.

```
AmniQuest (Root)
 ├── :app       (ANDROID_APP)       - UI Layer, Navigation, ViewModels, Room DBs, AppBlockerService, WorkManager Workers, AppWidgets
 ├── :core      (ANDROID_LIBRARY)   - Core Utilities, ECDSA NIST P-256 Licensing, UsageStats Helper, Hardware Managers, Time Tools
 ├── :data      (ANDROID_LIBRARY)   - Canonical Domain Models, Quest & Focus Configs, Game Mechanics, Serialization Contracts
 ├── :backend   (ANDROID_LIBRARY)   - Remote Validation Clients, Supabase DTOs, Gemini API Client
 └── :ai        (ANDROID_LIBRARY)   - Native SentencePiece C++ JNI, CMake (16 KB Page Aligned), SigLIP 2 Preprocessing & Tokenizer
```

### Module Responsibilities & Dependencies

| Module | Type | Responsibilities | Key Dependencies |
| :--- | :--- | :--- | :--- |
| **`:app`** | App | Presentation layer (Jetpack Compose), Navigation Host, ViewModels (unified `AiSnapQuestViewVM`), Room SQLite databases (`QuestDatabase`, `StatsDatabase`, `ReminderDatabase`, `WidgetDatabase`), Foreground `AppBlockerService`, WorkManager background workers (`FileDownloadWorker`, `BackupWorker`, `QuestSyncWorker`, `StatsSyncWorker`), AES-256 encrypted `BackupManager`, Supabase Client integration (`SupabaseManager`), AppWidget hosts, and Build Flavors (`play`, `fdroid`). | `:data`, `:core`, `:backend`, `:ai`, Room, Hilt, WorkManager, Supabase |
| **`:core`** | Library | Core platform and domain utilities: screentime helper (`ScreenUsageStatsHelper`), hardware & accessibility managers (`AccessibilityManager`, `PermissionManager`, `AppListManager`, `SoundManager`, `Vibrator`), offline cryptographic licensing verification (`LicenseValidator` using ECDSA NIST P-256), time formatting (`TimeTools`, `UnitsFormatter`), and error logging (`ErrorLogger`). | `:data`, `kotlinx-datetime`, `serialization-json` |
| **`:data`** | Library | Canonical domain data representations, quest configurations (`AiSnap`, `DeepFocus`), user profile contracts (`UserInfo`), screentime records (`ScreentimeStat`), game progression models (`Achievements`, `InventoryItem`, `Pet`, `StreakData`), and evaluation status enums (`AiEvaluationStep`). | `kotlinx-datetime`, `serialization-json`, Compose Material3 |
| **`:backend`** | Library | Remote task validation endpoints, multimodal Gemini 2.5 Flash API client (`GeminiValidator`), external token generation (`GenerateExtIntToken`), and Supabase task validation bridge (`AiSnapValidator`). | `:data`, `:core`, Ktor, WorkManager |
| **`:ai`** | Library | Native C++ SentencePiece tokenizer wrapper compiled via CMake and NDK with 16 KB page size alignment (`sentencepiece_jni.cpp`), mutex synchronization, model path caching, and Kotlin JNI bridge with SigLIP 2 image preprocessing (`OneShotTools.kt`). | Android NDK (r25c), CMake |

---

## 2. Product Flavors & Distribution Architecture

AmniQuest uses a two-dimensional product flavor strategy (`distribution`) to accommodate Google Play Store requirements while adhering strictly to F-Droid open-source guidelines. On-device local AI verification via ONNX Runtime and the `:ai` SentencePiece native library is a **universal foundation available across all build variants**.

```
+-----------------------------------------------------------------------------------+
|                                  :app Module                                      |
+------------------------------------------+----------------------------------------+
|              Play Flavor                 |             F-Droid Flavor             |
+------------------------------------------+----------------------------------------+
| - Google Play Billing Library            | - F-Droid Free / Direct Donation Mgr   |
| - Firebase Cloud Messaging (FCM)         | - F-Droid Push Provider (No GMS/FCM)   |
| - Google AICore Gemini Nano (System LLM) | - 100% Free & Open-Source (FOSS) SDKs  |
| - Calibrated SigLIP 2 Zero-Shot Fallback | - Local SigLIP 2 ONNX Zero-Shot Vision |
| - Local SigLIP 2 ONNX Zero-Shot Vision   | - Local SentencePiece C++ JNI (:ai)    |
| - Local SentencePiece C++ JNI (:ai)      | - User Private Gemini API & Cloud Sync |
| - User Private Gemini API & Cloud Sync   | - Offline-first Local Model Inference  |
+------------------------------------------+----------------------------------------+
```

1. **`play` Flavor (`isFdroid = false`)**:
   - Includes Google Play Billing Library (`com.android.billing:billing-ktx`) and Firebase Messaging (`com.google.firebase:firebase-messaging`).
   - Supports Google AICore system service (`com.google.ai.edge.aicore`) for local Gemini Nano reasoning, with automatic fallback to calibrated SigLIP 2 zero-shot vision validation when AICore is unavailable.
   - Fully supports on-device SigLIP 2 ONNX vision classification models (UINT8, INT8, Q4F16, FP16, FULL) and native SentencePiece C++ JNI.
   - Supports user-configured Private Gemini API Key (`Gemini 2.5 Flash`) and Supabase Cloud Server validation.

2. **`fdroid` Flavor (`isFdroid = true`)**:
   - 100% Free and Open-Source Software (FOSS) compliant with zero proprietary GMS or Firebase SDKs.
   - Replaces Google Play Billing with F-Droid direct donation / free billing manager (`FdroidBillingManager.kt`) and uses `FdroidPushProvider.kt`.
   - Fully supports on-device SigLIP 2 ONNX vision classification models (UINT8, INT8, Q4F16, FP16, FULL) paired with native SentencePiece C++ JNI.
   - Supports user-configured Private Gemini API Key and Supabase Cloud Server validation without requiring Google Play services.

---

## 3. Data Architecture & Persistence Blueprint

AmniQuest relies on an **Offline-First Synchronization Architecture** with local Room databases serving as the single source of truth, complemented by background cloud synchronization with Supabase.

```
+-----------------------------------------------------------------------+
|                             Presentation                              |
+-----------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------+
|                           QuestRepository                             |
+-----------------------------------------------------------------------+
                 /                                   \
                v                                     v
+-------------------------------+     +---------------------------------+
|     Room SQLite Database      |     |     Supabase Cloud Backend      |
|  (Quest, Stats, Reminders,    | <-> |   (Auth, Postgrest, Storage,    |
|            Widgets)           |     |         Profile Sync)           |
+-------------------------------+     +---------------------------------+
```

### Local Persistence (Room DBs)
- **`QuestDatabase`**: Stores active, completed, and template quests (`CommonQuestInfo`). Tracks unsynced changes via `is_synced = false`.
- **`StatsDatabase`**: Records screentime statistics, coin transactions, streak milestones, and daily activity logs (`StatsInfo`).
- **`ReminderDatabase`**: Manages scheduled alarm metadata and pending notifications (`ReminderData`).
- **`WidgetDatabase`**: Maintains home screen widget configurations.

### Security & Encryption
- **AES-256-CBC Backup Encryption**: Local database backups are exported using `PBKDF2` key derivation and `AES-256-CBC` encryption managed by `BackupManager.kt` and scheduled via `BackupWorker.kt`.
- **Habit Privacy Mode**: Sensitive quest titles can be masked and locked behind Android Biometric Authentication (Fingerprint/Face/PIN) using `androidx.biometric`.

---

## 4. Core System Services & App Blocker Logic

The app enforcement engine relies on a high-efficiency polling and system monitoring service operating in the background.

```
                                +---------------------------+
                                |    AppBlockerService      |
                                |  (Foreground Service)     |
                                +-------------+-------------+
                                              |
                                              v
                              +-------------------------------+
                              | UsageStatsManager Polling Loop|
                              |      (Every 100ms)            |
                              +---------------+---------------+
                                              |
                   +--------------------------+--------------------------+
                   |                                                     |
                   v                                                     v
      [Foreground App is Locked]                           [Foreground App is Unlocked]
                   |                                                     |
                   v                                                     v
     Show HomeScreen Lock Overlay                            Allow Normal App Execution
    (Redirect to MainActivity)                               (Update Cooldown Timers)
```

### Foreground App Blocking Workflow (`AppBlockerService.kt`)
1. **Service Execution**: Runs as an Android Foreground Service with `dataSync` / `specialUse` service types to ensure non-killable background execution.
2. **Polling Loop**: Evaluates `UsageStatsManager` events every **100ms**.
3. **Detection Logic**:
   - Queries `ACTIVITY_RESUMED` events to detect the active foreground package.
   - Cross-references package names against `lockedApps` set (configured in user profile / deep focus mode).
4. **Lock Action**:
   - If a locked app is launched without an active cooldown timer (`AppBlockerServiceInfo.unlockedApps`), `AppBlockerService` immediately launches `MainActivity` with `FLAG_ACTIVITY_REORDER_TO_FRONT` to block the screen.
5. **Deep Focus Mode**: Locks all non-essential applications except user-whitelisted exceptions and system keyboards.

---

## 5. Real-Life Quest Engine & AI Validation Logic

AmniQuest gamifies productivity by requiring users to prove they completed real-world tasks (e.g. making their bed, reading a book, going to the gym).

### Quest Types
1. **AI Snap Quest**: User captures a photo proof of task completion.
2. **Deep Focus Quest**: Monitored screen-time and app blocking session.
3. **Swift Mark Quest**: Recurrent habit check-off with optional auto-destruct timers.
4. **External Integration Quest**: Webview-based quest integration via custom JavaScript bridge (`WebAppInterface.kt`).

### AI Validation Architecture & Unified ViewModel

The AI verification pipeline is unified across all build variants via `app/src/main/java/com/alhaq/amniquest/AiSnapQuestViewVM.kt`. It provides real-time progress steps (`INITIALIZING`, `LOADING_IMAGE`, `CHECKING_MODEL`, `LOADING_MODEL`, `PREPROCESSING`, `TOKENIZING`, `EVALUATING`, `COMPLETED`), memory lifecycle management, and automatic runtime fallback.

```
                                  [Photo Captured by User]
                                             |
                                             v
                           Check Validation Engine Preference
                                             |
                 +---------------------------+---------------------------+
                 |                           |                           |
                 v                           v                           v
           Engine = "local"           Engine = "gemini_api"        Engine = "cloud"
                 |                           |                           |
                 v                           v                           v
         Model Selected from         Use User's Private           Retrieve Supabase
          SigLIP 2 Catalog             Gemini API Key               Access Token
                 |                           |                           |
                 +---------------+           |                           |
                 |               |           v                           v
                 v               v       Direct API Call             Send to Cloud
           SigLIP 2 ONNX    Gemini Nano   (Gemini 2.5 Flash)       Validation Endpoint
            Zero-Shot        (AICore)            |                           |
                 |               |               +-------------+-------------+
                 |               v                             |
                 |        Is AICore Active?                    |
                 |           /       \                         |
                 |         Yes        No                       |
                 |          |          |                       |
                 |          v          v                       |
                 |       Prompt    Fallback                    |
                 |       Gemini    to SigLIP 2                 |
                 |        Nano     Zero-Shot                   |
                 |          |          |                       |
                 +----------+----------+                       |
                            |                                  |
                            v                                  v
                  Return Validation Result <-------------------+
                     (Valid / Invalid)
```

### Supported Validation Engines & Model Catalog

AmniQuest supports four distinct validation engines:

1. **Local SigLIP 2 Zero-Shot Vision Engine (`local`)**:
   - Executes 100% locally on-device using ONNX Runtime (`onnxruntime-android`) and the native SentencePiece C++ JNI.
   - Built-in model catalog supports 5 quantized and full-precision variants:
     - `SIGLIP2_UINT8` (Quantized UINT8, ~378 MB): Lowest memory footprint, optimized for entry-level devices.
     - `SIGLIP2_INT8` (Quantized INT8, ~378 MB): Balanced inference performance and memory.
     - `SIGLIP2_Q4F16` (Quantized Q4 with FP16 outputs, ~497 MB): High precision with reduced weights.
     - `SIGLIP2_FP16` (Half-Precision Float16, ~751 MB): Native FP16 floating-point execution.
     - `SIGLIP2_FULL` (Full Precision Float32, ~1500 MB): Reference full-precision vision model.
   - Dynamic model resolution falls back immediately to the embedded static catalog if network connectivity is unavailable, preventing null pointer crashes.

2. **System Gemini Nano Engine (`gemini_nano`)**:
   - Evaluates verification prompts on supported Google Pixel and Samsung flagship devices using Google's system-level `AICore` client API (`LocalGeminiNanoValidator.kt`).
   - Hardened JSON response parsing (`parseNanoValidationResponse`) handles standard schemas, reversed key ordering (`{"reason": "...", "valid": true}`), and escaped strings.
   - **Calibrated Fallback**: If `AICore` is unavailable, uninitialized, or fails at runtime, the pipeline automatically falls back to calibrated SigLIP 2 zero-shot similarity verification.

3. **Private Gemini API Key Engine (`gemini_api`)**:
   - Directly calls Google's `Gemini 2.5 Flash` API endpoint (`GeminiValidator.kt`) using the user's personal API key stored in private preferences.

4. **Cloud Server Validation Engine (`cloud`)**:
   - Authenticates with Supabase JWT and transmits the photo payload to the remote task validation endpoint (`AiSnapValidator.kt`).

### Multi-Precision Tensor Extraction & Buffer Safety

Different ONNX quantized architectures produce varying output logit tensor types. The extraction pipeline in `AiPipelineUtils.extractLogitsFromBuffers` handles:
- **`FLOAT`**: Direct `FloatBuffer` instances are safely copied into `FloatArray` without calling `.array()`, avoiding `UnsupportedOperationException` on direct NIO buffers.
- **`FLOAT16` & `BFLOAT16`**: Output `shortBuffer` instances are converted to 32-bit floats using `Fp16Conversions.convertFp16BufferToFloatBuffer(shortBuffer)`.
- **`DOUBLE`**: Extracted via `doubleBuffer` and cast down to 32-bit floats for cosine similarity calculation.

### Native SentencePiece C++ JNI & Gemma Tokenization Specs

The native tokenizer module (`:ai`) provides text tokenization for zero-shot prompts:
- **Thread Safety**: Model loading and tokenization are protected by `std::mutex` in `sentencepiece_jni.cpp` to prevent race conditions during concurrent worker evaluations.
- **Model Path Caching**: Cached global model path (`g_loaded_model_path`) prevents redundant disk reloads when the same model file is reused.
- **Gemma Tokenization Specs**: Configured with `PAD=0L`, `EOS=1L`, `maxLength=64`, and lowercase normalization in `OneShotTools.kt`.
- **16 KB Memory Page Alignment**: Compiled with `-Wl,-z,max-page-size=16384` for strict Android 15+ 16 KB page memory alignment compliance.

### Hardware Acceleration & Runtime Fault Recovery

- **NNAPI Acceleration**: Sessions initialize with NNAPI hardware acceleration enabled by default.
- **Automatic CPU Recovery**: If NNAPI execution fails during `session.run(inputs)` due to driver incompatibility or unsupported tensor shapes, the session automatically catches the runtime exception, recreates a CPU-only `OrtSession`, and completes verification without user disruption.
- **Memory & Bitmap Lifecycle**:
  - `Bitmap.Config.HARDWARE` instances are safely converted to software bitmaps before pixel extraction.
  - Bulk pixel extraction via `getPixels()` replaces slow pixel-by-pixel reads.
  - Intermediate camera bitmaps are explicitly recycled in `try-finally` blocks.
  - All `OnnxTensor` instances are enclosed in `.use { ... }` blocks to ensure immediate native memory release.
- **Error Lockout Prevention**: `resetResults()` clears `error.value` and resets `currentStep` to `INITIALIZING`, allowing users to retry validation without remaining trapped on error screens.

### Resilient Model Download Engine (`FileDownloadWorker`)

On-device AI models (~378 MB to ~1.5 GB) and tokenizer assets (~4.1 MB) are downloaded via Android `WorkManager`:
- **HTTP Range Resume**: Supports HTTP 206 Partial Content resumes and HTTP 200 clean restarts when network interrupts occur.
- **Notification Synchronization**: Dedicated foreground notification IDs prevent notification channel collisions.
- **Shared Tokenizer Queue**: Tokenizer downloads use a unique work name (`tokenizer.model`) with `ExistingWorkPolicy.KEEP` to prevent redundant downloads.
- **WorkManager Expedited Quota Fallback**: Uses `RUN_AS_NON_EXPEDITED_WORK_REQUEST` to prevent silent job drops when system expedited quotas are exhausted.
- **State Reconciliation & Cancellation**: `ModelDownloadDialog.kt` reconciles stale SharedPreferences flags against active WorkManager states and provides an explicit "Cancel Download" option.

---

## 6. Gamification System & Economy Mechanics

```
    [Complete Quests] ---> [Earn XP & Coins] ---> [Level Up Profile]
                                  |
                                  v
                      [Spend Coins in Store]
                                  |
            +---------------------+---------------------+
            |                                           |
            v                                           v
[Unlock App Usage Cooldowns]                   [Feed & Evolve Pet /
  (e.g., 15 mins YouTube)                       Virtual Companion]
```

- **XP & Level Progression**: Completing quests awards XP based on difficulty and streak multiplier. High level-ups trigger the `LevelUpDialog.kt` celebration.
- **Streak Mechanics**: Daily completion increments streaks. Missed days consume "Streak Freezers" or fail the streak (`StreakFailedDialog.kt`).
- **Coin Economy (`StoreScreen.kt`)**: Users spend earned coins to purchase temporary app unblock passes or store items.
- **Virtual Companion / Pet System (`Pet.kt`, `TheSystemDialog.kt`)**: A companion pet whose health and evolution depend on habit consistency.

---

## 7. UI/UX Architecture & Theme Engine

- **Framework**: Built 100% declaratively using **Jetpack Compose** and **Material Design 3**.
- **Navigation**: Structured using Compose Navigation with typed `RootRoute` graphs.
- **Custom Theme Engine**: Supports 7 distinct themes:
  - Cyberpunk
  - Nordic Frost
  - Solarized Sunset
  - Cherry Blossoms
  - Hacker
  - Pitch Black
  - Bonsai
- **Interactive Canvas Animations**: Custom Compose canvas shaders and animations including Matrix digital rain (`MatrixAnimation.kt`), Sakura blossom particle physics (`FocusSakura.kt`), and procedurally growing Bonsai trees (`FocusingBonsaiTree.kt`).
- **Home Screen Launcher**: Minimalist launcher mode (`HomeScreen.kt`, `AppList.kt`) designed to reduce phone scrolling impulses.

---

## 8. Build & Compilation Specifications

### Toolchain Requirements
- **JDK**: Java 17
- **Android SDK**: Compile SDK 36, Target SDK 36, Min SDK 26
- **NDK & CMake**: NDK 25.2.9519653, CMake 3.22.1+
- **Android 15+ 16 KB Memory Page Alignment**:
  - Native shared libraries compiled in `:ai` explicitly configure `-Wl,-z,max-page-size=16384` in `CMakeLists.txt`.
  - `:app` packaging options enforce `useLegacyPackaging = false` to preserve 16 KB alignment in uncompressed APK/AAB distributions.

### Gradle Commands

```powershell
# Run entire automated test suite across all modules
.\gradlew.bat test

# Build both Play Debug and F-Droid Debug APKs
.\gradlew.bat --gradle-user-home D:\PROJECTS\Amni\AmniQuest\.gradle_home assemblePlayDebug assembleFdroidDebug

# Build Play Release Bundle (AAB)
.\gradlew.bat --gradle-user-home D:\PROJECTS\Amni\AmniQuest\.gradle_home bundlePlayRelease
```

---

## Summary Matrix

| Domain | Architecture Pattern / Tech Stack | Primary Implementation Files |
| :--- | :--- | :--- |
| **Presentation Layer** | Jetpack Compose + Hilt ViewModels | `MainActivity.kt`, `HomeScreen.kt`, `RootRoute.kt`, `SetAiSnap.kt`, `SetupTemplate.kt` |
| **Enforcement Service** | Foreground Service + UsageStatsManager | `AppBlockerService.kt`, `AppBlockerServiceInfo.kt` |
| **Data & Sync** | Room SQLite + Supabase Postgrest | `CommonQuestInfo.kt`, `StatsInfo.kt`, `SupabaseManager.kt` |
| **Unified AI Validation** | SigLIP 2 ONNX / Gemini Nano / Gemini API | `AiSnapQuestViewVM.kt`, `LocalGeminiNanoValidator.kt`, `GeminiValidator.kt`, `AiPipelineUtils.kt` |
| **Native C++ Tokenizer** | CMake + JNI + SentencePiece (16 KB Aligned) | `ai/src/main/cpp/CMakeLists.txt`, `sentencepiece_jni.cpp`, `OneShotTools.kt` |
| **Model Downloads** | Android WorkManager (Range Resume, Non-Expedited) | `FileDownloadWorker.kt`, `ModelDownloadDialog.kt` |
| **Background Tasks** | Android WorkManager (Backups & Sync) | `BackupWorker.kt`, `QuestSyncWorker.kt`, `StatsSyncWorker.kt` |
| **Gamification** | Room DB + Custom State Engines | `StoreScreen.kt`, `LevelUpDialog.kt`, `TheSystemDialog.kt` |
