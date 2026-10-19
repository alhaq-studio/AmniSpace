# AmniQuest Project Agent Guidelines (`AGENTS.md`)

Unified baseline instructions for AmniQuest across Google Antigravity, Anthropic Claude Code, OpenAI Codex, and GitHub Copilot.

---

## 1. Module Boundaries

- **`:app`**: Presentation layer (Jetpack Compose, Material 3, Edge-to-Edge), unified `AiSnapQuestViewVM` (calibrated SigLIP 2 zero-shot similarity, negative margin gating, thread-safe session caching), Room databases (`QuestDatabase`, `StatsDatabase`, `ReminderDatabase`, `WidgetDatabase`), foreground `AppBlockerService`, WorkManager background workers (`FileDownloadWorker` with redirect and concurrency safety, `BackupWorker`, `QuestSyncWorker`, `StatsSyncWorker` with anonymous guards), encrypted `BackupManager`, and `SupabaseManager`.
- **`:core`**: Pure domain and platform utilities (`ScreenUsageStatsHelper`, `LicenseValidator` using ECDSA NIST P-256, hardware and accessibility managers, `TimeTools`, `ErrorLogger`).
- **`:data`**: Canonical domain data models, Kotlinx Serialization contracts, and `AiEvaluationStep` enums.
- **`:backend`**: Remote task validation endpoints, multimodal `GeminiValidator` API client.
- **`:ai`**: Native SentencePiece C++ JNI compiled via CMake and NDK with 16 KB page memory alignment, pure-Kotlin protobuf and Unigram Viterbi fallback (`KotlinSentencePieceModel`), mutex synchronization, model caching, and SigLIP 2 preprocessing (`OneShotTools.kt`).

---

## 2. Verification & Build Commands

- **Unit Tests**: `./gradlew testDebugUnitTest`
- **AI Module Unit Tests**: `./gradlew :ai:testDebugUnitTest`
- **Release Verification Gate**: `./gradlew lintVitalRelease` (or `./gradlew lintVitalFdroidRelease`)
- **Assemble Universal APK**: `./gradlew assembleFdroidRelease`
- **Emoji Invariant Check**: `python ~/.agents/plugins/amni-ecosystem/hooks/scripts/check_no_emojis.py`
- **Flavor ViewModel Check**: `python ~/.agents/plugins/amni-ecosystem/hooks/scripts/verify_flavor_sources.py`

---

## 3. Mandatory Skill & Rule Triggers

- BEFORE modifying JNI, CMake, ONNX, LiteRT, or Tokenizers -> MUST activate `android-local-ai-onnx` skill.
- BEFORE modifying `play` or `fdroid` source sets -> MUST check `experimental-fork-porting` rule (never duplicate ViewModels across flavors; unify in `src/main`).
- BEFORE modifying ECDSA licensing, ProGuard, or Supabase RLS -> MUST activate `security-compliance` skill.
- BEFORE modifying Stripe subscriptions, checkout sessions, or webhooks -> MUST activate `stripe-payments` skill.
- BEFORE modifying Supabase Edge Functions, database migrations, or schemas -> MUST activate `supabase-backend` skill.
- BEFORE drafting store copy, release notes, changelogs, or ASO -> MUST activate `marketing-growth` skill.
- AFTER completing any feature or bug fix that alters module structure or public behavior -> MUST run `sync-docs` check on `ARCHITECTURE.md`.

---

## 4. Central Plugin Reference

Inherits core ecosystem invariants from:
`C:\Users\HabiburRahman\.agents\plugins\amni-ecosystem\rules\AGENTS.md`
