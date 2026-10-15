# AmniQuest v0.0.1 Architecture, Blueprint & Technical Logic Specification

## Executive Overview
**AmniQuest v0.0.1** (developed under Al-Haq Studio as part of the Amn Ecosystem) is a fresh rebuild, complete redesign, and fork of **QuestPhone** / **PhoneQuest** originally created by **Neithicle** (`neithicl`). It is an open-source, privacy-first Android minimal launcher and habit gamification framework designed to combat screen addiction by locking distracting applications behind real-life quests verified by on-device AI or external integration systems. Versioning starts fresh at **v0.0.1**.

---

## 1. Multi-Module Project Blueprint

The project follows a modular Clean Architecture structure using Gradle multi-project builds.

```
AmniQuest (Root)
 ├── :app       (ANDROID_APP)       - UI Layer, Navigation, ViewModels, Blocker Service, Hilt Modules, AppWidgets
 ├── :core      (ANDROID_LIBRARY)   - Domain Utils, Licensing, UsageStats, Notification Schedulers, Time Tools
 ├── :data      (ANDROID_LIBRARY)   - Core Data Models, Entities, Common Quest Definitions, Preferences
 ├── :backend   (ANDROID_LIBRARY)   - Remote API Integrations, Supabase Auth/Storage/Postgrest, Gemini Validators
 └── :ai        (ANDROID_LIBRARY)   - Native SentencePiece C++ JNI, CMake compilation, Local Tokenizer
```

### Module Responsibilities & Dependencies

| Module | Type | Responsibilities | Key Dependencies |
| :--- | :--- | :--- | :--- |
| **`:app`** | App | Presentation layer (Jetpack Compose), Navigation Host, ViewModels, Accessibility Service, Hilt DI bindings, AppWidget hosts, Build Flavors (`play`, `fdroid`). | `:data`, `:core`, `:backend`, `:ai` (fdroid) |
| **`:core`** | Library | App blocking services, usage statistics polling, AES-256 backup logic, FCM handling, licensing validator, sound & vibration managers. | `:data` |
| **`:data`** | Library | Canonical data representations, Kotlinx Serialization contracts, Room database schemas, Quest info classes, User profile models. | `kotlinx-datetime`, `serialization-json`, Compose Material3 |
| **`:backend`** | Library | Ktor HTTP client, Supabase authentication & storage handlers, remote task validation endpoints, Gemini API client. | `:data`, `:core`, Ktor, WorkManager |
| **`:ai`** | Library | Native C++ SentencePiece tokenizer wrapper compiled via CMake and NDK, JNI bindings for local NLP/vision pipelines. | Android NDK (r25c), CMake |

---

## 2. Product Flavors & Distribution Architecture

AmniQuest uses a strict two-dimensional product flavor strategy (`distribution`) to accommodate Google Play Store requirements while adhering to F-Droid open-source guidelines.

```
+-----------------------------------------------------------------------------------+
|                                  :app Module                                      |
+------------------------------------------+----------------------------------------+
|              Play Flavor                 |             F-Droid Flavor             |
+------------------------------------------+----------------------------------------+
| - Google Play Billing Library            | - ONNX Runtime Android Engine          |
| - Firebase Cloud Messaging (FCM)         | - Local SentencePiece JNI (:ai)        |
| - System Gemini Nano (Google AICore)     | - Local SigLIP ONNX Zero-Shot Vision   |
| - Cloud / Private Gemini API Fallback    | - Offline-first Local Model Inference  |
+------------------------------------------+----------------------------------------+
```

1. **`play` Flavor (`isFdroid = false`)**:
   - Includes closed-source/proprietary SDKs: Google Play Billing Library (`com.android.billing:billing-ktx`) and Firebase Messaging (`com.google.firebase:firebase-messaging`).
   - Uses `Google AICore` (`com.google.ai.edge.aicore`) for system-level Gemini Nano on supported devices.
   - Leverages `Cloud Server` or user-configured `Private Gemini API Key` for multimodal photo validation.

2. **`fdroid` Flavor (`isFdroid = true`)**:
   - 100% Free and Open-Source Software (FOSS) compliant.
   - Replaces Google Play Billing with F-Droid direct donation / free billing manager (`FdroidBillingManager.kt`).
   - Integrates `:ai` native module and ONNX Runtime Android (`ai.onnxruntime:onnxruntime-android`).
   - Executes local zero-shot image classification via SigLIP ONNX models paired with the SentencePiece tokenizer JNI.

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

### AI Validation Chain (`AiSnapQuestViewVM.kt`)

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
  Is Gemini Nano             Use User's Private           Retrieve Supabase
  Supported?                 Gemini API Key               Access Token
    /      \                         |                           |
  Yes       No                       |                           |
   |         |                       v                           v
   v         v             Send Multimodal Prompt       Send to Remote Server
 Validate   Fallback        to Gemini 2.5 Flash         Validation Endpoint
 Locally   to SigLIP                 |                           |
 via Nano  (F-Droid)                 +-------------+-------------+
                                                   |
                                                   v
                                        Return Validation Result
                                           (Valid / Invalid)
```

1. **Local Gemini Nano Engine**: Executes on-device LLM prompt evaluation using Google's system-level `AICore` client API (`LocalGeminiNanoValidator.kt`). Zero network latency and total privacy.
2. **Private Gemini API Key**: Uses the user's personal Gemini API key (`GeminiValidator.kt`) to make direct multimodal calls to `Gemini 2.5 Flash`.
3. **Cloud Validation Server**: Authenticates with Supabase JWT and passes image payloads to the backend validation endpoint (`AiSnapValidator.kt`).
4. **Local SigLIP (F-Droid Local Engine)**: Runs ONNX Runtime image preprocessing, tokenizes feature tags with native SentencePiece JNI, and computes zero-shot feature logits.

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

### Gradle Commands

```powershell
# Build both Play Debug and F-Droid Debug APKs
.\gradlew.bat --gradle-user-home D:\PROJECTS\Amni\AmniQuest\.gradle_home assemblePlayDebug assembleFdroidDebug

# Build Play Release Bundle (AAB)
.\gradlew.bat --gradle-user-home D:\PROJECTS\Amni\AmniQuest\.gradle_home bundlePlayRelease
```

---

## Summary Matrix

| Domain | Architecture Pattern / Tech Stack | Primary Implementation Files |
| :--- | :--- | :--- |
| **Presentation Layer** | Jetpack Compose + Hilt ViewModels | `MainActivity.kt`, `HomeScreen.kt`, `RootRoute.kt` |
| **Enforcement Service** | Foreground Service + UsageStatsManager | `AppBlockerService.kt`, `AppBlockerServiceInfo.kt` |
| **Data & Sync** | Room SQLite + Supabase Postgrest | `CommonQuestInfo.kt`, `StatsInfo.kt`, `SupabaseManager.kt` |
| **AI Validation** | Gemini Nano / Gemini API / SigLIP ONNX | `LocalGeminiNanoValidator.kt`, `GeminiValidator.kt`, `AiSnapQuestViewVM.kt` |
| **Native C++ Tokenizer** | CMake + JNI + SentencePiece | `ai/src/main/cpp/CMakeLists.txt`, `sentencepiece_jni.cpp` |
| **Background Tasks** | Android WorkManager | `BackupWorker.kt`, `QuestSyncWorker.kt`, `FileDownloadWorker.kt` |
| **Gamification** | Room DB + Custom State Engines | `StoreScreen.kt`, `LevelUpDialog.kt`, `TheSystemDialog.kt` |
