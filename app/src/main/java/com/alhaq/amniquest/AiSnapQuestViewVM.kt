package com.alhaq.amniquest

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.platform.Fp16Conversions
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.lifecycle.viewModelScope
import com.alhaq.amniquest.ai.OneShotTools
import com.alhaq.amniquest.ai.SentencePieceProcessor
import com.alhaq.amniquest.app.screens.quest.setup.ai_snap.model.SIGLIP2_TOKENIZER_URL
import com.alhaq.amniquest.app.screens.quest.setup.ai_snap.model.ensureTokenizerQueued
import com.alhaq.amniquest.app.screens.quest.view.ViewQuestVM
import com.alhaq.amniquest.app.screens.quest.view.ai_snap.AI_SNAP_PIC
import com.alhaq.amniquest.backed.repositories.QuestRepository
import com.alhaq.amniquest.backed.repositories.StatsRepository
import com.alhaq.amniquest.backed.repositories.UserRepository
import com.alhaq.amniquest.backend.GeminiValidator
import com.alhaq.amniquest.backend.TaskValidationClient
import com.alhaq.amniquest.core.Supabase
import com.alhaq.amniquest.core.utils.LocalGeminiNanoValidator
import com.alhaq.amniquest.core.workers.FileDownloadWorker
import com.alhaq.amniquest.data.EvaluationStep
import com.alhaq.amniquest.data.json
import com.alhaq.amniquest.data.quest.ai.snap.AiSnap
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.random.Random

@HiltViewModel
class AiSnapQuestViewVM @Inject constructor(
    questRepository: QuestRepository,
    userRepository: UserRepository,
    statsRepository: StatsRepository,
    private val context: Application,
) : ViewQuestVM(
    questRepository, userRepository, statsRepository, context,
) {
    companion object {
        private const val TAG = "AiSnapQuestViewVM"
        private const val TOKENIZER_FILE_NAME = "tokenizer.model"

        // SigLIP 2 calibrated thresholds:
        // Sigmoid over learned temperature+bias logits (`logits_per_image`):
        // Unrelated text prompts score < 0.005; genuine matches score >= 0.05..0.95.
        private const val SIGLIP_SIGMOID_THRESHOLD = 0.05f
        // Cosine similarity over L2-normalized embeddings (`image_embeds` dot `text_embeds`):
        private const val SIGLIP_COSINE_THRESHOLD = 0.16f
        // Required margin above negative baseline prompts:
        private const val NEGATIVE_MARGIN_LOGIT = 0.25f
        private const val NEGATIVE_MARGIN_COSINE = 0.015f

        private val NEGATIVE_BASELINE_PROMPTS = listOf(
            "this is a blurry, dark, or irrelevant photo.",
            "this is a photo of a blank wall, floor, or empty background."
        )
    }

    val isAiEvaluating = MutableStateFlow(false)
    val isCameraScreen = MutableStateFlow(false)
    var aiQuest = AiSnap()

    val currentStep = MutableStateFlow(EvaluationStep.INITIALIZING)
    val error = MutableStateFlow<String?>(null)
    val results = MutableStateFlow<TaskValidationClient.ValidationResult?>(null)
    val isModelDownloaded = MutableStateFlow(true)

    private var modelSession: OrtSession? = null
    private var env: OrtEnvironment? = null
    private var isModelLoaded = false
    private var isSessionUsingNnapi = false
    private var loadedModelFilePath: String? = null
    private val sessionLock = Any()

    private var modelId: String = "online"
    private var isOnlineInferencing = true

    private val client = TaskValidationClient()
    private val localNano = LocalGeminiNanoValidator(context)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadModel(forEvaluation = false)
        }
    }

    fun setAiSnap() {
        if (commonQuestInfo.quest_json.isNotBlank()) {
            try {
                aiQuest = json.decodeFromString<AiSnap>(commonQuestInfo.quest_json)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode AiSnap quest_json", e)
            }
        }
    }

    fun onAiSnapQuestDone() {
        saveMarkedQuestToDb()
        isCameraScreen.value = false
    }

    /**
     * Resolves the local ONNX model file from SharedPreferences or scans filesDir for any
     * downloaded .onnx model, migrating legacy unquantized FP32 preferences automatically.
     */
    private fun resolveLocalOnnxModelFile(): File? {
        val sp = context.getSharedPreferences("models", Context.MODE_PRIVATE)
        var selected = sp.getString("selected_one_shot_model", null)

        // Migrate legacy unquantized FP32 model IDs
        if (selected == "model" || selected == "fp32" || selected == "full_fp32") {
            selected = "SIGLIP2_FULL"
            sp.edit(commit = true) {
                putString("selected_one_shot_model", selected)
            }
        }

        if (!selected.isNullOrBlank() && selected != "online") {
            val candidate = File(context.filesDir, "$selected.onnx")
            if (candidate.exists() && candidate.isFile && candidate.length() > 1024L) {
                modelId = selected
                return candidate
            }
        }

        // Scan filesDir for any valid downloaded .onnx model in priority order
        val preferredOrder = listOf(
            "small", "medium", "full",
            "SIGLIP2_UINT8", "SIGLIP2_INT8", "SIGLIP2_Q4", "SIGLIP2_Q4F16", "SIGLIP2_FP16", "SIGLIP2_FULL"
        )
        for (prefId in preferredOrder) {
            val file = File(context.filesDir, "$prefId.onnx")
            if (file.exists() && file.isFile && file.length() > 1024L) {
                modelId = prefId
                sp.edit(commit = true) {
                    putString("selected_one_shot_model", prefId)
                    putBoolean("is_downloaded_$prefId", true)
                }
                return file
            }
        }

        val anyOnnx = context.filesDir.listFiles()?.firstOrNull {
            it.isFile && it.extension.equals("onnx", ignoreCase = true) && it.length() > 1024L
        }
        if (anyOnnx != null) {
            val inferredId = anyOnnx.nameWithoutExtension
            modelId = inferredId
            sp.edit(commit = true) {
                putString("selected_one_shot_model", inferredId)
                putBoolean("is_downloaded_$inferredId", true)
            }
            return anyOnnx
        }

        return null
    }

    /**
     * Ensures tokenizer.model is present in filesDir and loaded into SentencePieceProcessor.
     * Downloads synchronously if missing so native JNI never crashes on an absent file.
     */
    private fun ensureTokenizerReady(): File? {
        val tokenizerFile = File(context.filesDir, TOKENIZER_FILE_NAME)
        val sp = context.getSharedPreferences("models", Context.MODE_PRIVATE)

        if (!tokenizerFile.exists() || tokenizerFile.length() <= 1024L) {
            Log.i(TAG, "tokenizer.model missing or incomplete, downloading synchronously...")
            val tempFile = File(context.filesDir, "$TOKENIZER_FILE_NAME.sync.part")
            try {
                val request = Request.Builder().url(SIGLIP2_TOKENIZER_URL).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code} downloading tokenizer.model")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty body for tokenizer.model")
                    FileOutputStream(tempFile).use { out ->
                        body.byteStream().copyTo(out)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 1024L) {
                    if (tokenizerFile.exists()) tokenizerFile.delete()
                    if (!tempFile.renameTo(tokenizerFile)) {
                        tempFile.copyTo(tokenizerFile, overwrite = true)
                        tempFile.delete()
                    }
                    sp.edit(commit = true) {
                        putBoolean("is_downloaded_tokenizer", true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Synchronous tokenizer.model download failed: ${e.message}", e)
                tempFile.delete()
                FileDownloadWorker.enqueueTokenizerDownload(context)
                return null
            }
        }

        return if (SentencePieceProcessor.ensureLoaded(tokenizerFile.absolutePath)) {
            tokenizerFile
        } else {
            Log.e(TAG, "SentencePieceProcessor failed to load tokenizer.model")
            null
        }
    }

    fun loadModel(forEvaluation: Boolean = false): Boolean {
        val settingsSp = context.getSharedPreferences("private_settings", Context.MODE_PRIVATE)
        val defaultEngine = if (BuildConfig.IS_FDROID) "local" else "cloud"
        val engine = settingsSp.getString("validation_engine", defaultEngine) ?: defaultEngine

        if (engine != "local") {
            isOnlineInferencing = true
            isModelLoaded = true
            isModelDownloaded.value = true
            return true
        }

        isOnlineInferencing = false
        val modelFile = resolveLocalOnnxModelFile()
        if (modelFile == null) {
            isModelLoaded = false
            val nanoAvail = localNano.isAvailable()
            isModelDownloaded.value = nanoAvail
            if (forEvaluation && !nanoAvail) {
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Local SigLIP 2 ONNX model is not downloaded yet. Please tap the download icon to download the offline vision model."
                )
                currentStep.value = EvaluationStep.COMPLETED
            }
            return nanoAvail
        }

        return synchronized(sessionLock) {
            try {
                val canonicalPath = modelFile.canonicalPath
                if (modelSession != null && loadedModelFilePath == canonicalPath) {
                    isModelLoaded = true
                    isModelDownloaded.value = true
                    return@synchronized true
                }

                if (forEvaluation) {
                    currentStep.value = EvaluationStep.LOADING_MODEL
                }

                try {
                    modelSession?.close()
                } catch (_: Throwable) {}
                modelSession = null

                val environment = env ?: OrtEnvironment.getEnvironment().also { env = it }
                var createdSession: OrtSession? = null
                var usingNnapi = false

                OrtSession.SessionOptions().use { nnapiOpts ->
                    val nnapiAdded = try {
                        nnapiOpts.addNnapi()
                        true
                    } catch (e: Throwable) {
                        Log.w(TAG, "NNAPI not available: ${e.message}")
                        false
                    }
                    nnapiOpts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                    nnapiOpts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    if (nnapiAdded) {
                        try {
                            createdSession = environment.createSession(canonicalPath, nnapiOpts)
                            usingNnapi = true
                        } catch (e: Throwable) {
                            Log.w(TAG, "Failed to create session with NNAPI, falling back to CPU: ${e.message}")
                        }
                    }
                }

                if (createdSession == null) {
                    OrtSession.SessionOptions().use { cpuOpts ->
                        cpuOpts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
                        cpuOpts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                        cpuOpts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                        createdSession = environment.createSession(canonicalPath, cpuOpts)
                        usingNnapi = false
                    }
                }

                modelSession = createdSession
                isSessionUsingNnapi = usingNnapi
                loadedModelFilePath = canonicalPath
                isModelDownloaded.value = true
                isModelLoaded = true
                Log.i(TAG, "Loaded local SigLIP 2 ONNX session from $canonicalPath (NNAPI: $usingNnapi)")
                true
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to initialize ONNX session from ${modelFile.absolutePath}", e)
                if (forEvaluation) {
                    results.value = TaskValidationClient.ValidationResult(
                        isValid = false,
                        reason = "Failed to load local ONNX model (${modelFile.name}): ${e.message}"
                    )
                    currentStep.value = EvaluationStep.COMPLETED
                }
                isModelLoaded = false
                false
            }
        }
    }

    private fun recreateCpuSession(environment: OrtEnvironment): OrtSession {
        try {
            modelSession?.close()
        } catch (_: Throwable) {}
        val modelFile = File(context.filesDir, "$modelId.onnx")
        val cpuSession = OrtSession.SessionOptions().use { cpuOpts ->
            cpuOpts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            cpuOpts.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            cpuOpts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            environment.createSession(modelFile.absolutePath, cpuOpts)
        }
        modelSession = cpuSession
        isSessionUsingNnapi = false
        loadedModelFilePath = modelFile.canonicalPath
        return cpuSession
    }

    fun evaluateQuest(onEvaluationComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            resetResults()
            val photoFile = File(context.filesDir, AI_SNAP_PIC)
            if (!photoFile.exists() || photoFile.length() == 0L) {
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Captured image file not found. Please retake the photo."
                )
                currentStep.value = EvaluationStep.COMPLETED
                return@launch
            }

            val settingsSp = context.getSharedPreferences("private_settings", Context.MODE_PRIVATE)
            val modelsSp = context.getSharedPreferences("models", Context.MODE_PRIVATE)
            val selectedModel = modelsSp.getString("selected_one_shot_model", null)
            val defaultEngine = if (BuildConfig.IS_FDROID || (selectedModel != null && selectedModel != "online")) {
                "local"
            } else {
                "cloud"
            }
            val engine = settingsSp.getString("validation_engine", defaultEngine) ?: defaultEngine

            when (engine) {
                "local" -> runOfflineInference(photoFile, onEvaluationComplete)
                "gemini_api" -> {
                    val compressedFile = resizeAndCompressImage(photoFile, 1080, 50)
                    runGeminiApiInference(compressedFile, settingsSp.getString("gemini_api_key", null), onEvaluationComplete)
                }
                else -> {
                    val compressedFile = resizeAndCompressImage(photoFile, 1080, 50)
                    runCloudInference(compressedFile, onEvaluationComplete)
                }
            }
        }
    }

    private suspend fun runOfflineInference(
        photoFile: File,
        onEvaluationComplete: () -> Unit
    ) {
        currentStep.value = EvaluationStep.LOADING_MODEL

        val modelFile = resolveLocalOnnxModelFile()
        if (modelFile == null) {
            if (localNano.isAvailable()) {
                currentStep.value = EvaluationStep.EVALUATING
                val fallbackFeatures = aiQuest.features.ifEmpty { listOf(aiQuest.taskDescription) }
                val nanoResult = localNano.validateTaskLocally(aiQuest.taskDescription, fallbackFeatures)
                results.value = nanoResult
                currentStep.value = EvaluationStep.COMPLETED
                if (nanoResult.isValid) {
                    onEvaluationComplete()
                }
                return
            }

            isModelDownloaded.value = false
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Local SigLIP 2 ONNX model is not downloaded yet. Please tap the download icon to download an offline model."
            )
            currentStep.value = EvaluationStep.COMPLETED
            return
        }

        if (!loadModel(forEvaluation = true) || modelSession == null) {
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Failed to load local ONNX model (${modelFile.name}). The model file may be corrupted; please re-download it."
            )
            currentStep.value = EvaluationStep.COMPLETED
            return
        }

        val tokenizerFile = ensureTokenizerReady()
        if (tokenizerFile == null) {
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "SigLIP 2 tokenizer (tokenizer.model) has not finished downloading. Please connect to the internet briefly and retry."
            )
            currentStep.value = EvaluationStep.COMPLETED
            return
        }

        currentStep.value = EvaluationStep.PREPROCESSING

        val bitmap = decodeBitmapWithExif(photoFile)
        if (bitmap == null) {
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Could not decode captured photo. Please retake the snap."
            )
            currentStep.value = EvaluationStep.COMPLETED
            return
        }

        val rawPositiveFeatures = buildList {
            aiQuest.features.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
            if (isEmpty() && aiQuest.taskDescription.isNotBlank()) {
                add(aiQuest.taskDescription.trim())
            }
        }

        if (rawPositiveFeatures.isEmpty()) {
            bitmap.recycle()
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Quest has no task description or features configured."
            )
            currentStep.value = EvaluationStep.COMPLETED
            return
        }

        val formattedPositivePrompts = rawPositiveFeatures.map { OneShotTools.formatSiglipPrompt(it) }
        val allPrompts = formattedPositivePrompts + NEGATIVE_BASELINE_PROMPTS
        val batchSize = allPrompts.size
        val seqLen = OneShotTools.MAX_SEQ_LEN

        val flatInputIds = LongArray(batchSize * seqLen)
        val flatAttentionMask = LongArray(batchSize * seqLen)

        currentStep.value = EvaluationStep.TOKENIZING

        try {
            for (b in 0 until batchSize) {
                val paddedIds = OneShotTools.tokenizeText(
                    text = allPrompts[b],
                    tokenizerModelPath = tokenizerFile.absolutePath,
                    maxLength = seqLen
                )
                val offset = b * seqLen
                for (i in 0 until seqLen) {
                    val tokenId = paddedIds[i]
                    flatInputIds[offset + i] = tokenId
                    flatAttentionMask[offset + i] = if (tokenId != OneShotTools.PAD_TOKEN_ID.toLong()) 1L else 0L
                }
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            bitmap.recycle()
            Log.e(TAG, "Tokenization failed", e)
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Local tokenization error: ${e.message}"
            )
            currentStep.value = EvaluationStep.COMPLETED
            return
        }

        val pixelBuffer = try {
            OneShotTools.preprocessBitmapToFloatBuffer(bitmap)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            bitmap.recycle()
            Log.e(TAG, "Image preprocessing failed", e)
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Image preprocessing error: ${e.message}"
            )
            currentStep.value = EvaluationStep.COMPLETED
            return
        } finally {
            bitmap.recycle()
        }

        currentStep.value = EvaluationStep.EVALUATING

        val environment = env ?: OrtEnvironment.getEnvironment().also { env = it }
        val session = modelSession!!

        var inputIdsTensor: OnnxTensor? = null
        var pixelValuesTensor: OnnxTensor? = null
        var attentionMaskTensor: OnnxTensor? = null
        var ortResult: OrtSession.Result? = null

        try {
            val textShape = longArrayOf(batchSize.toLong(), seqLen.toLong())
            val inputIdsType = (session.inputInfo["input_ids"]?.info as? TensorInfo)?.type
            inputIdsTensor = if (inputIdsType == OnnxJavaType.INT32) {
                val intIds = IntArray(flatInputIds.size) { flatInputIds[it].toInt() }
                OnnxTensor.createTensor(environment, IntBuffer.wrap(intIds), textShape)
            } else {
                OnnxTensor.createTensor(environment, LongBuffer.wrap(flatInputIds), textShape)
            }

            val imageShape = longArrayOf(1L, 3L, OneShotTools.IMAGE_SIZE.toLong(), OneShotTools.IMAGE_SIZE.toLong())
            val pixelValuesType = (session.inputInfo["pixel_values"]?.info as? TensorInfo)?.type
            pixelValuesTensor = when (pixelValuesType) {
                OnnxJavaType.FLOAT16 -> {
                    val fp16Buf = Fp16Conversions.convertFloatBufferToFp16Buffer(pixelBuffer)
                    OnnxTensor.createTensor(environment, fp16Buf, imageShape, OnnxJavaType.FLOAT16)
                }
                OnnxJavaType.BFLOAT16 -> {
                    val bf16Buf = Fp16Conversions.convertFloatBufferToBf16Buffer(pixelBuffer)
                    OnnxTensor.createTensor(environment, bf16Buf, imageShape, OnnxJavaType.BFLOAT16)
                }
                else -> {
                    OnnxTensor.createTensor(environment, pixelBuffer, imageShape)
                }
            }

            val inputs = mutableMapOf<String, OnnxTensor>()
            val expectedInputs = session.inputNames

            val inputIdsName = expectedInputs.firstOrNull { it.contains("input_ids") } ?: "input_ids"
            val pixelValuesName = expectedInputs.firstOrNull { it.contains("pixel_values") } ?: "pixel_values"
            inputs[inputIdsName] = inputIdsTensor
            inputs[pixelValuesName] = pixelValuesTensor

            val attentionMaskName = expectedInputs.firstOrNull { it.contains("attention_mask") }
            if (attentionMaskName != null) {
                val maskType = (session.inputInfo[attentionMaskName]?.info as? TensorInfo)?.type
                attentionMaskTensor = if (maskType == OnnxJavaType.INT32) {
                    val maskInts = IntArray(flatAttentionMask.size) { flatAttentionMask[it].toInt() }
                    OnnxTensor.createTensor(environment, IntBuffer.wrap(maskInts), textShape)
                } else {
                    OnnxTensor.createTensor(environment, LongBuffer.wrap(flatAttentionMask), textShape)
                }
                inputs[attentionMaskName] = attentionMaskTensor
            }

            ortResult = try {
                session.run(inputs)
            } catch (runError: Throwable) {
                if (isSessionUsingNnapi) {
                    Log.w(TAG, "NNAPI execution failed at runtime, recreating session on CPU: ${runError.message}")
                    val cpuSession = recreateCpuSession(environment)
                    cpuSession.run(inputs)
                } else {
                    throw runError
                }
            }

            val scoring = extractSiglipScores(ortResult, batchSize, formattedPositivePrompts.size)

            val matchedFeatures = mutableListOf<String>()
            val featureReport = mutableListOf<String>()

            for (i in rawPositiveFeatures.indices) {
                val featureName = rawPositiveFeatures[i]
                val prob = scoring.probabilities[i]
                val rawMetric = scoring.rawMetrics[i]
                val passed = scoring.passedFlags[i]
                val pct = (prob * 100f).toInt().coerceIn(0, 100)
                featureReport.add("$featureName ($pct%)")
                if (passed) {
                    matchedFeatures.add(featureName)
                }
                Log.i(
                    TAG,
                    "Feature '$featureName': prob=$prob, raw=$rawMetric, negMax=${scoring.maxNegativeMetric}, passed=$passed"
                )
            }

            val requiredMatches = max(1, (rawPositiveFeatures.size + 1) / 2)
            val siglipValid = matchedFeatures.size >= requiredMatches

            // Local Gemini Nano reasoning pipeline integration (when required visual features are detected)
            if (localNano.isAvailable() && siglipValid) {
                val nanoValidation = localNano.validateTaskLocally(
                    taskDescription = aiQuest.taskDescription,
                    detectedFeatures = matchedFeatures
                )
                if (nanoValidation.isValid) {
                    results.value = nanoValidation
                    currentStep.value = EvaluationStep.COMPLETED
                    onEvaluationComplete()
                    return
                } else {
                    Log.i(TAG, "Gemini Nano returned non-valid or unavailable (${nanoValidation.reason}), using SigLIP 2 ONNX result")
                }
            }

            val reasonText = if (siglipValid) {
                "Verified locally with SigLIP 2 ($modelId): detected ${matchedFeatures.joinToString(", ")}. Scores: ${featureReport.joinToString(", ")}."
            } else {
                "Could not confidently detect required features (${rawPositiveFeatures.joinToString(", ")}) in the photo. Scores: ${featureReport.joinToString(", ")}. Try taking a clearer, well-lit photo."
            }

            results.value = TaskValidationClient.ValidationResult(
                isValid = siglipValid,
                reason = reasonText
            )
            currentStep.value = EvaluationStep.COMPLETED
            if (siglipValid) {
                onEvaluationComplete()
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.e(TAG, "ONNX inference execution failed", e)
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Local ONNX inference error: ${e.message ?: "Unexpected failure"}"
            )
            currentStep.value = EvaluationStep.COMPLETED
        } finally {
            try {
                ortResult?.close()
            } catch (_: Throwable) {}
            try {
                inputIdsTensor?.close()
            } catch (_: Throwable) {}
            try {
                pixelValuesTensor?.close()
            } catch (_: Throwable) {}
            try {
                attentionMaskTensor?.close()
            } catch (_: Throwable) {}
        }
    }

    private data class SiglipScoringResult(
        val probabilities: FloatArray,
        val rawMetrics: FloatArray,
        val passedFlags: BooleanArray,
        val maxNegativeMetric: Float
    )

    /**
     * Extracts and calibrates scores from SigLIP 2 ONNX outputs.
     * Supports both `logits_per_image` / `logits_per_text` and `image_embeds` + `text_embeds`.
     */
    private fun extractSiglipScores(
        ortResult: OrtSession.Result,
        batchSize: Int,
        positiveCount: Int
    ): SiglipScoringResult {
        val outputMap = mutableMapOf<String, Any?>()
        for (entry in ortResult) {
            outputMap[entry.key] = entry.value?.value
        }

        val logitsPerImage = extractFloatVector(outputMap["logits_per_image"], batchSize)
            ?: extractTransposedFloatVector(outputMap["logits_per_text"], batchSize)

        val imageEmbeds = extract2DFloatArray(outputMap["image_embeds"])?.firstOrNull()
        val textEmbeds = extract2DFloatArray(outputMap["text_embeds"])

        val cosineSims: FloatArray? = if (imageEmbeds != null && textEmbeds != null && textEmbeds.size >= batchSize) {
            FloatArray(batchSize) { i ->
                OneShotTools.cosineSimilarity(imageEmbeds, textEmbeds[i])
            }
        } else {
            null
        }

        val probabilities = FloatArray(positiveCount)
        val rawMetrics = FloatArray(positiveCount)
        val passedFlags = BooleanArray(positiveCount)

        if (logitsPerImage != null) {
            var maxNegLogit = Float.NEGATIVE_INFINITY
            for (i in positiveCount until batchSize) {
                if (logitsPerImage[i] > maxNegLogit) {
                    maxNegLogit = logitsPerImage[i]
                }
            }
            if (maxNegLogit == Float.NEGATIVE_INFINITY) maxNegLogit = -10.0f

            for (i in 0 until positiveCount) {
                val logit = logitsPerImage[i]
                val prob = OneShotTools.sigmoid(logit)
                val cosSim = cosineSims?.getOrNull(i)
                probabilities[i] = prob
                rawMetrics[i] = logit

                val passesLogit = prob >= SIGLIP_SIGMOID_THRESHOLD &&
                    (logit >= maxNegLogit + NEGATIVE_MARGIN_LOGIT || prob >= 0.20f)
                val passesCosine = cosSim != null && cosSim >= SIGLIP_COSINE_THRESHOLD
                passedFlags[i] = passesLogit || passesCosine
            }

            return SiglipScoringResult(
                probabilities = probabilities,
                rawMetrics = rawMetrics,
                passedFlags = passedFlags,
                maxNegativeMetric = maxNegLogit
            )
        }

        if (cosineSims != null) {
            var maxNegCos = Float.NEGATIVE_INFINITY
            for (i in positiveCount until batchSize) {
                if (cosineSims[i] > maxNegCos) {
                    maxNegCos = cosineSims[i]
                }
            }
            if (maxNegCos == Float.NEGATIVE_INFINITY) maxNegCos = 0.0f

            for (i in 0 until positiveCount) {
                val cos = cosineSims[i]
                val calibratedLogit = (cos - 0.14f) * 15.0f
                val prob = OneShotTools.sigmoid(calibratedLogit)
                probabilities[i] = prob
                rawMetrics[i] = cos
                passedFlags[i] = cos >= SIGLIP_COSINE_THRESHOLD &&
                    (cos >= maxNegCos + NEGATIVE_MARGIN_COSINE || cos >= 0.22f)
            }

            return SiglipScoringResult(
                probabilities = probabilities,
                rawMetrics = rawMetrics,
                passedFlags = passedFlags,
                maxNegativeMetric = maxNegCos
            )
        }

        // Fallback for custom output tensor name
        val firstOutput = outputMap.values.firstOrNull()
        val fallbackVec = extractFloatVector(firstOutput, batchSize)
            ?: FloatArray(batchSize) { 0.0f }

        var maxNeg = Float.NEGATIVE_INFINITY
        for (i in positiveCount until batchSize) {
            if (fallbackVec[i] > maxNeg) maxNeg = fallbackVec[i]
        }
        for (i in 0 until positiveCount) {
            val v = fallbackVec[i]
            val prob = OneShotTools.sigmoid(v)
            probabilities[i] = prob
            rawMetrics[i] = v
            passedFlags[i] = prob >= SIGLIP_SIGMOID_THRESHOLD && v > maxNeg
        }

        return SiglipScoringResult(
            probabilities = probabilities,
            rawMetrics = rawMetrics,
            passedFlags = passedFlags,
            maxNegativeMetric = maxNeg
        )
    }

    private fun extractFloatVector(raw: Any?, expectedSize: Int): FloatArray? {
        if (raw == null) return null
        return when (raw) {
            is FloatArray -> if (raw.size >= expectedSize) raw else null
            is Array<*> -> {
                val first = raw.firstOrNull()
                if (first is FloatArray && first.size >= expectedSize) {
                    first
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun extractTransposedFloatVector(raw: Any?, expectedSize: Int): FloatArray? {
        if (raw is Array<*> && raw.size >= expectedSize) {
            val result = FloatArray(expectedSize)
            for (i in 0 until expectedSize) {
                val row = raw[i] as? FloatArray ?: return null
                if (row.isEmpty()) return null
                result[i] = row[0]
            }
            return result
        }
        return null
    }

    private fun extract2DFloatArray(raw: Any?): Array<FloatArray>? {
        if (raw is Array<*>) {
            val rows = mutableListOf<FloatArray>()
            for (elem in raw) {
                if (elem is FloatArray) {
                    rows.add(elem)
                }
            }
            if (rows.isNotEmpty()) {
                return rows.toTypedArray()
            }
        }
        return null
    }

    private fun decodeBitmapWithExif(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (rotationDegrees == 0f) {
                rawBitmap
            } else {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap from ${file.absolutePath}", e)
            null
        }
    }

    private suspend fun runGeminiApiInference(
        compressedFile: File,
        geminiKey: String?,
        onEvaluationComplete: () -> Unit
    ) {
        if (geminiKey.isNullOrBlank()) {
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Private Gemini API key is missing. Please configure it in your Profile settings or switch to Local On-Device AI."
            )
            currentStep.value = EvaluationStep.COMPLETED
            return
        }

        currentStep.value = EvaluationStep.LOADING_MODEL
        val geminiValidator = GeminiValidator()
        geminiValidator.validateTask(
            compressedFile,
            aiQuest.taskDescription,
            aiQuest.features.joinToString(","),
            geminiKey
        ) { result ->
            results.value = result.getOrNull() ?: TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Gemini validation failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            )
            currentStep.value = EvaluationStep.COMPLETED
            if (results.value?.isValid == true) {
                onEvaluationComplete()
            }
        }

        val allSteps = EvaluationStep.entries
        var currentStepInt = 0
        while (results.value == null) {
            delay(Random.nextInt(500, 1500).toLong())
            currentStep.value = EvaluationStep.valueOf(allSteps[currentStepInt].name)
            if (currentStepInt != EvaluationStep.EVALUATING.ordinal) currentStepInt++
        }
    }

    private suspend fun runCloudInference(
        compressedFile: File,
        onEvaluationComplete: () -> Unit
    ) {
        currentStep.value = EvaluationStep.LOADING_MODEL
        val token = if (userRepository.userInfo.isAnonymous) {
            ""
        } else {
            try {
                Supabase.supabase.auth.currentAccessTokenOrNull()?.toString() ?: ""
            } catch (_: Exception) {
                ""
            }
        }

        if (token.isEmpty()) {
            if (resolveLocalOnnxModelFile() != null) {
                Log.i(TAG, "Cloud auth unavailable, falling back to downloaded local SigLIP 2 ONNX model")
                runOfflineInference(File(context.filesDir, AI_SNAP_PIC), onEvaluationComplete)
                return
            }
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Authentication required for Cloud Server. Please log in, configure a Private Gemini API Key, or select Local On-Device AI."
            )
            currentStep.value = EvaluationStep.COMPLETED
            return
        }

        client.validateTask(
            compressedFile,
            aiQuest.taskDescription,
            aiQuest.features.joinToString(","),
            token
        ) { result ->
            results.value = result.getOrNull() ?: TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Online validation failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
            )
            currentStep.value = EvaluationStep.COMPLETED
            if (results.value?.isValid == true) {
                onEvaluationComplete()
            }
        }

        val allSteps = EvaluationStep.entries
        var currentStepInt = 0
        while (results.value == null) {
            delay(Random.nextInt(500, 1500).toLong())
            currentStep.value = EvaluationStep.valueOf(allSteps[currentStepInt].name)
            if (currentStepInt != EvaluationStep.EVALUATING.ordinal) currentStepInt++
        }
    }

    fun resetResults() {
        isAiEvaluating.value = true
        results.value = null
        error.value = null
        currentStep.value = EvaluationStep.INITIALIZING
    }

    override fun onCleared() {
        super.onCleared()
        synchronized(sessionLock) {
            try {
                modelSession?.close()
                env?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close ONNX session", e)
            } finally {
                modelSession = null
                env = null
                loadedModelFilePath = null
                isModelLoaded = false
                isSessionUsingNnapi = false
            }
        }
    }
}

fun resizeAndCompressImage(file: File, maxSize: Int = 1080, quality: Int = 70): File {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return file
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        bitmap.recycle()
        return file
    }

    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    val width: Int
    val height: Int
    if (ratio > 1) {
        width = maxSize
        height = (maxSize / ratio).toInt().coerceAtLeast(1)
    } else {
        height = maxSize
        width = (maxSize * ratio).toInt().coerceAtLeast(1)
    }

    val scaledBitmap = bitmap.scale(width, height)
    val compressedFile = File(file.parent, "compressed_upload.jpg")
    FileOutputStream(compressedFile).use { out ->
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        out.flush()
    }
    if (scaledBitmap != bitmap) {
        scaledBitmap.recycle()
    }
    bitmap.recycle()

    return compressedFile
}
