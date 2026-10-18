# Project: AmniQuest v0.0.1 Architecture & System Blueprint

## Overview
**AmniQuest v0.0.1** (developed under Al-Haq Studio as part of the Amni Ecosystem) is a fresh rebuild, complete redesign, and fork of [QuestPhone](https://github.com/QuestPhone/questphone) originally created by **Nethical**. It is an open-source, privacy-first Android minimal launcher and habit gamification framework engineered to combat screen addiction by locking distracting applications behind real-life quests verified through on-device AI or external cloud integrations.

For detailed technical specifications, module breakdowns, and data flow diagrams, refer to [ARCHITECTURE.md](ARCHITECTURE.md).

---

## 1. Multi-Module Project Structure

| Module | Type | Primary Responsibilities |
| :--- | :--- | :--- |
| **`:app`** | Android Application | Jetpack Compose presentation layer, navigation graph, unified `AiSnapQuestViewVM`, Room SQLite database implementations (`QuestDatabase`, `StatsDatabase`, `ReminderDatabase`, `WidgetDatabase`), `AppBlockerService` foreground monitor, WorkManager background workers (`FileDownloadWorker`, `BackupWorker`, `QuestSyncWorker`, `StatsSyncWorker`), AES-256 encrypted `BackupManager`, Supabase client sync, AppWidget hosts, and distribution flavors (`play`, `fdroid`). |
| **`:core`** | Android Library | Platform and domain utility helpers, screentime usage statistics helper (`ScreenUsageStatsHelper`), hardware & accessibility managers (`AccessibilityManager`, `PermissionManager`, `AppListManager`, `SoundManager`, `Vibrator`), offline cryptographic licensing verification (`LicenseValidator` using ECDSA NIST P-256), time tools (`TimeTools`, `UnitsFormatter`), and `ErrorLogger`. |
| **`:data`** | Android Library | Canonical domain data models, Kotlinx Serialization contracts, quest configurations (`AiSnap`, `DeepFocus`), user profiles (`UserInfo`), screentime records (`ScreentimeStat`), game progression models (`Achievements`, `InventoryItem`, `Pet`, `StreakData`), and `EvaluationStep` status enums. |
| **`:backend`** | Android Library | Remote task validation endpoints, multimodal Gemini 2.5 Flash API client (`GeminiValidator`), external token generation (`GenerateExtIntToken`), and Supabase task validation bridge (`AiSnapValidator`). |
| **`:ai`** | Android Library | Native C++ SentencePiece tokenizer wrapper compiled via CMake and NDK with 16 KB page alignment (`sentencepiece_jni.cpp`), mutex synchronization, model path caching, and Kotlin JNI bridge with SigLIP 2 image preprocessing (`OneShotTools.kt`). |

---

## 2. Core Architecture & Logic Highlights

### A. Foreground App Blocker Service (`AppBlockerService`)
- Operates as a persistent Foreground Service (`specialUse` / `dataSync`) utilizing `UsageStatsManager` to poll active foreground apps every **100ms**.
- Intercepts launches of user-locked apps or active Deep Focus sessions, redirecting the user to `MainActivity` to enforce quest completion or biometric verification.

### B. Unified Multi-Tier AI Validation Chain (`AiSnapQuestViewVM`)
Unified in `app/src/main/` across all build variants. Tracks 8 real-time evaluation states (`INITIALIZING`, `LOADING_IMAGE`, `CHECKING_MODEL`, `LOADING_MODEL`, `PREPROCESSING`, `TOKENIZING`, `EVALUATING`, `COMPLETED`), memory lifecycle management, and automatic runtime fallback. Supports four distinct validation backends configurable via user preferences (`validation_engine`):
1. **Local SigLIP 2 Zero-Shot Vision Engine (`local`)**:
   - Executes 100% on-device using ONNX Runtime and the `:ai` SentencePiece native library.
   - Built-in catalog provides 5 quantized and full-precision models: `SIGLIP2_UINT8` (378 MB), `SIGLIP2_INT8` (378 MB), `SIGLIP2_Q4F16` (497 MB), `SIGLIP2_FP16` (751 MB), and `SIGLIP2_FULL` (1500 MB).
   - Multi-precision buffer extraction (`AiPipelineUtils.kt`) supports `FLOAT`, `FLOAT16` (`Fp16Conversions`), `BFLOAT16`, and `DOUBLE`. Direct NIO buffers are handled without `.array()` call crashes.
   - Hardware-accelerated via NNAPI with automatic runtime error recovery falling back to CPU.
   - Native SentencePiece JNI incorporates `std::mutex` synchronization, model path caching, Gemma tokenization specs (`PAD=0L`, `EOS=1L`, `maxLength=64`), and Android 15+ 16 KB page alignment (`-Wl,-z,max-page-size=16384`).
   - Hardware bitmap safe conversion and bulk `getPixels()` extraction with explicit bitmap recycling.
2. **Local Gemini Nano (`gemini_nano`)**:
   - On-device LLM prompt evaluation via Google `AICore` (`LocalGeminiNanoValidator.kt`).
   - Robust JSON parsing supporting standard/reversed key schemas and escaped quotes.
   - Automatic fallback: If `AICore` is unavailable or unsupported on the device, seamlessly falls back to calibrated SigLIP 2 zero-shot similarity scores.
3. **Private Gemini API Key (`gemini_api`)**: Direct multimodal calls to `Gemini 2.5 Flash` using the user's personal API key (`GeminiValidator.kt`).
4. **Cloud Server Validation (`cloud`)**: Remote validation endpoint authenticated via Supabase JWT (`AiSnapValidator.kt`).

### C. Resilient Model Download Engine (`FileDownloadWorker` & `ModelDownloadDialog`)
- **HTTP Range Resume**: Handles HTTP 206 Partial Content resumes and HTTP 200 restarts upon connection interruptions.
- **Dedicated Tokenizer Queue**: Tokenizer downloads use a unique queue (`tokenizer.model`) with `ExistingWorkPolicy.KEEP` to prevent redundant downloads.
- **Non-Expedited Fallback**: Enforces `RUN_AS_NON_EXPEDITED_WORK_REQUEST` to prevent silent task drops when expedited job quotas are depleted.
- **Offline Dialog Fallback**: Embedded static catalog fallback avoids `NullPointerException` during network disconnects.
- **State Reconciliation & Cancellation**: Reconciles stale SharedPreferences flags against active WorkManager workers and provides download cancellation.

### D. Offline-First Storage & Supabase Sync
- **Room SQLite Databases**: Single source of truth for quests, screentime stats, coin transactions, and reminders.
- **Background Synchronization**: `WorkManager` workers (`QuestSyncWorker`, `StatsSyncWorker`, `BackupWorker`) handle rolling encrypted AES-256 local backups and offline-to-cloud Supabase synchronization.

### E. Gamification & Custom Themes
- **Economy & Progression**: Quest completion awards XP, coin currency, and streak milestones with automatic streak freezers.
- **Virtual Companion**: Pet/companion system whose vitality responds to habit consistency.
- **Custom Themes**: 7 immersive themes (Cyberpunk, Nordic Frost, Solarized Sunset, Cherry Blossoms, Hacker, Pitch Black, Bonsai) with custom canvas shaders and particle animations.

---

## 3. Product Distribution Flavors

- **`play` (Google Play Store)**:
  - Includes Google Play Billing Library and Firebase Cloud Messaging (FCM).
  - Supports system-level Gemini Nano (`AICore`) with calibrated SigLIP 2 zero-shot fallback.
  - Fully supports on-device SigLIP 2 ONNX models (UINT8, INT8, Q4F16, FP16, FULL) and native SentencePiece C++ JNI.
  - Supports Cloud and Private Gemini API validation.
- **`fdroid` (F-Droid FOSS)**:
  - 100% Free and Open-Source Software compliant with zero proprietary GMS/Firebase dependencies.
  - Uses `FdroidBillingManager.kt` and `FdroidPushProvider.kt`.
  - Fully supports on-device SigLIP 2 ONNX models (UINT8, INT8, Q4F16, FP16, FULL) and native SentencePiece C++ JNI.
  - Supports Cloud and Private Gemini API validation without requiring Google Play Services.

---

## 4. Development Milestones & Roadmap

### v0.0.1 Baseline Release (Completed)
| # | Milestone | Scope | Status |
|---|-----------|-------|--------|
| 1 | Multi-Module Clean Architecture | Modularize app into `:app`, `:core`, `:data`, `:backend`, and `:ai` with Android 15+ 16 KB page alignment | **COMPLETED** |
| 2 | Unified AI Validation Chain | Unified `AiSnapQuestViewVM` supporting SigLIP 2 ONNX, Gemini Nano AICore, Private Gemini API, and Cloud Server | **COMPLETED** |
| 3 | Local SigLIP 2 Vision Suite | Deploy and verify 5 local SigLIP 2 variants (UINT8, INT8, Q4F16, FP16, FULL) with multi-precision tensor extraction | **COMPLETED** |
| 4 | Resilient Model Download Engine | Range resume (HTTP 206), tokenizer work queue, non-expedited fallback, and offline catalog resilience | **COMPLETED** |
| 5 | Foreground App Blocker | 100ms UsageStats polling service with Deep Focus mode | **COMPLETED** |
| 6 | Gamification & Theme Suite | Leveling, streak management, coin store, virtual pet, and 7 canvas themes | **COMPLETED** |
| 7 | Enhanced Backup & Biometric Lock | AES-256-CBC encrypted backups via WorkManager and biometric habit privacy mode | **COMPLETED** |

### Future Roadmap
| Version | Target Feature / Initiative | Description | Status |
| :--- | :--- | :--- | :--- |
| **v0.1.0** | Advanced Model Profiling & NPU Optimization | Dynamic NPU delegate selection, runtime benchmark metrics, and inference latency auto-tuning across SoCs. | Planned |
| **v0.2.0** | Advanced Widget Ecosystem | Interactive home screen AppWidgets for quick habit logging, streak tracking, and focus timers. | In Design |
| **v0.3.0** | Wear OS Companion App | Smartwatch companion app for quick habit check-ins and active focus session monitoring. | Planned |
| **v0.4.0** | Peer Accountability Network | Optional encrypted friend challenges and streak sharing via Supabase Realtime. | Exploring |
