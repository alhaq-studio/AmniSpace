# Project: AmniQuest v0.0.1 Architecture & System Blueprint

## Overview
**AmniQuest v0.0.1** (developed under Al-Haq Studio as part of the Amni Ecosystem) is a fresh rebuild, complete redesign, and fork of [QuestPhone](https://github.com/QuestPhone/questphone) originally created by **Nethical**. It is an open-source, privacy-first Android minimal launcher and habit gamification framework engineered to combat screen addiction by locking distracting applications behind real-life quests verified through on-device AI or external cloud integrations.

For detailed technical specifications, module breakdowns, and data flow diagrams, refer to [ARCHITECTURE.md](ARCHITECTURE.md).

---

## 1. Multi-Module Project Structure

| Module | Type | Primary Responsibilities |
| :--- | :--- | :--- |
| **`:app`** | Android Application | Jetpack Compose presentation layer, navigation graph, ViewModels, `AppBlockerService`, AppWidget hosts, and distribution flavors (`play`, `fdroid`). |
| **`:core`** | Android Library | Background utility services, app blocking logic, screentime usage stats helpers, notification schedulers, backup manager, and crash logging. |
| **`:data`** | Android Library | Domain data models, Kotlinx Serialization contracts, Room SQLite database schemas (`CommonQuestInfo`, `StatsInfo`, `ReminderData`), and local preferences. |
| **`:backend`** | Android Library | Ktor HTTP client, Supabase Auth/Storage/Postgrest integration, remote task validation endpoints, and Gemini API client. |
| **`:ai`** | Android Library | Native C++ SentencePiece tokenizer wrapper compiled via CMake and NDK, providing local NLP tokenization for F-Droid SigLIP ONNX models. |

---

## 2. Core Architecture & Logic Highlights

### A. Foreground App Blocker Service (`AppBlockerService`)
- Operates as a persistent Foreground Service (`specialUse` / `dataSync`) utilizing `UsageStatsManager` to poll active foreground apps every **100ms**.
- Intercepts launches of user-locked apps or active Deep Focus sessions, redirecting the user to `MainActivity` to enforce quest completion or biometric verification.

### B. Multi-Tier AI Validation Chain (`AiSnapQuestViewVM`)
Supports four distinct validation backends configurable via user preferences (`validation_engine`):
1. **Local Gemini Nano**: On-device LLM prompt evaluation via Google `AICore` (`LocalGeminiNanoValidator.kt`).
2. **Private Gemini API Key**: Direct multimodal calls to `Gemini 2.5 Flash` using the user's personal API key (`GeminiValidator.kt`).
3. **Cloud Server Validation**: Remote validation endpoint authenticated via Supabase JWT (`AiSnapValidator.kt`).
4. **Local SigLIP (F-Droid)**: Offline zero-shot image classification using ONNX Runtime and native SentencePiece JNI tokenization.

### C. Offline-First Storage & Supabase Sync
- **Room SQLite Databases**: Single source of truth for quests, screentime stats, coin transactions, and reminders.
- **Background Synchronization**: `WorkManager` workers (`QuestSyncWorker`, `StatsSyncWorker`, `BackupWorker`) handle rolling encrypted AES-256 local backups and offline-to-cloud Supabase synchronization.

### D. Gamification & Custom Themes
- **Economy & Progression**: Quest completion awards XP, coin currency, and streak milestones with automatic streak freezers.
- **Virtual Companion**: Pet/companion system whose vitality responds to habit consistency.
- **Custom Themes**: 7 immersive themes (Cyberpunk, Nordic Frost, Solarized Sunset, Cherry Blossoms, Hacker, Pitch Black, Bonsai) with custom canvas shaders and particle animations.

---

## 3. Product Distribution Flavors

- **`play` (Google Play Store)**:
  - Includes Google Play Billing Library and Firebase Cloud Messaging (FCM).
  - Utilizes system-level Gemini Nano (`AICore`) and Cloud/Private Gemini API validation.
- **`fdroid` (F-Droid FOSS)**:
  - Replaces Play Billing with F-Droid direct billing manager.
  - Bundles ONNX Runtime Android and `:ai` native SentencePiece JNI for 100% offline-first SigLIP vision evaluation.

---

## 4. Development Milestones & Roadmap

### v0.0.1 Baseline Release (Completed)
| # | Milestone | Scope | Status |
|---|-----------|-------|--------|
| 1 | Multi-Module Clean Architecture | Modularize app into `:app`, `:core`, `:data`, `:backend`, and `:ai` | **COMPLETED** |
| 2 | Multi-Tier AI Validation Chain | Integrate Local Gemini Nano, Private Gemini API, Cloud API, and SigLIP ONNX | **COMPLETED** |
| 3 | Foreground App Blocker | 100ms UsageStats polling service with Deep Focus mode | **COMPLETED** |
| 4 | Gamification & Theme Suite | Leveling, streak management, coin store, virtual pet, and 7 canvas themes | **COMPLETED** |
| 5 | Enhanced Backup & Biometric Lock | AES-256-CBC encrypted backups via WorkManager and biometric habit privacy mode | **COMPLETED** |

### Future Roadmap
| Version | Target Feature / Initiative | Description | Status |
| :--- | :--- | :--- | :--- |
| **v0.1.0** | Quantized Vision Performance | Further memory and inference speed optimizations for F-Droid SigLIP ONNX models on budget devices. | Planned |
| **v0.2.0** | Advanced Widget Ecosystem | Interactive home screen AppWidgets for quick habit logging, streak tracking, and focus timers. | In Design |
| **v0.3.0** | Wear OS Companion App | Smartwatch companion app for quick habit check-ins and active focus session monitoring. | Planned |
| **v0.4.0** | Peer Accountability Network | Optional encrypted friend challenges and streak sharing via Supabase Realtime. | Exploring |
