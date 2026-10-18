# AmniQuest Development Guidelines (`GEMINI.md`)

You are the Amn Ecosystem Master Agent working on **AmniQuest** (`d:\PROJECTS\Amni\AmniQuest`), an open-source, privacy-first Android minimal launcher and habit gamification framework.

---

## 1. Multi-Module Boundaries

- **`:app`**: Presentation layer (Jetpack Compose, Material 3, Edge-to-Edge), unified `AiSnapQuestViewVM`, Room databases (`QuestDatabase`, `StatsDatabase`, `ReminderDatabase`, `WidgetDatabase`), foreground `AppBlockerService`, WorkManager background workers (`FileDownloadWorker`, `BackupWorker`), encrypted `BackupManager`, and `SupabaseManager`.
- **`:core`**: Pure domain & platform utilities (`ScreenUsageStatsHelper`, `LicenseValidator` using ECDSA NIST P-256, hardware and accessibility managers, `TimeTools`, `ErrorLogger`).
- **`:data`**: Canonical domain data models, Kotlinx Serialization contracts, and `AiEvaluationStep` enums.
- **`:backend`**: Remote task validation endpoints, multimodal `GeminiValidator` API client.
- **`:ai`**: Native SentencePiece C++ JNI compiled via CMake and NDK with 16 KB page memory alignment, mutex synchronization, model caching, and SigLIP 2 preprocessing (`OneShotTools.kt`).

---

## 2. On-Device AI & Native Memory Standards

1. **Direct NIO Buffer Extraction**: Never call `.array()` on an ONNX Runtime direct `FloatBuffer`. Always copy via:
   ```kotlin
   val copy = floatBuffer.duplicate()
   val array = FloatArray(copy.remaining())
   copy.get(array)
   ```
2. **Multi-Precision Output Logits**: Support `FLOAT`, `FLOAT16` (`Fp16Conversions.convertFp16BufferToFloatBuffer`), `BFLOAT16`, and `DOUBLE` output tensors.
3. **Hardware Bitmap Safety**: Guard against `Bitmap.Config.HARDWARE` via software copy (`ARGB_8888`), use bulk `getPixels(IntArray, ...)`, and recycle bitmaps in `try-finally` blocks.
4. **Native Tensor Lifecycle**: Wrap all `OnnxTensor` instances and `OrtSession.run()` results in `.use { ... }` blocks to prevent unmanaged C++ native memory leaks.
5. **CMake 16 KB Page Alignment**: Ensure `target_link_options(<target> PRIVATE "-Wl,-z,max-page-size=16384")` is present in `CMakeLists.txt`.
6. **SentencePiece Vocab Specs**: Use Gemma token IDs (`PAD = 0L`, `EOS = 1L`, `maxLength = 64`) and do not concatenate literal `" </s>"` strings into raw input queries.
7. **WorkManager Resilient Downloads**: Handle HTTP 200 vs 206 responses to prevent Range corruption, and use `RUN_AS_NON_EXPEDITED_WORK_REQUEST`.
8. **Runtime Fault Recovery**: If NNAPI execution fails during `session.run(inputs)`, catch the exception, recreate a CPU-only `OrtSession`, and complete verification seamlessly.

---

## 3. Strict Universal UI & Code Quality Rule: No Emojis

1. **Zero Emoji Policy**: Do NOT use UTF-8 emojis or emoji-based unicode characters anywhere in code, UI layouts, components, strings, comments, logs, commit messages, or documentation across any project file.
2. **Icons**: Use Material Symbols, Vector Drawables (`ic_*.xml`), or Compose `ImageVector`. Use plain-text tags (`[OK]`, `[FAIL]`, `[INFO]`, `[WARNING]`, `[ERROR]`) for status indicators.
