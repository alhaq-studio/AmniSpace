package com.alhaq.amniquest

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.platform.Fp16Conversions
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.lifecycle.viewModelScope
import com.alhaq.amniquest.ai.padTokenIds
import com.alhaq.amniquest.ai.preprocessBitmapToFloatBuffer
import com.alhaq.amniquest.ai.tokenizeText
import com.alhaq.amniquest.app.screens.quest.view.ViewQuestVM
import com.alhaq.amniquest.app.screens.quest.view.ai_snap.AI_SNAP_PIC
import com.alhaq.amniquest.app.screens.quest.view.ai_snap.getBitmapFromPath
import com.alhaq.amniquest.backed.repositories.QuestRepository
import com.alhaq.amniquest.backed.repositories.StatsRepository
import com.alhaq.amniquest.backed.repositories.UserRepository
import com.alhaq.amniquest.backend.GeminiValidator
import com.alhaq.amniquest.backend.TaskValidationClient
import com.alhaq.amniquest.core.Supabase
import com.alhaq.amniquest.core.utils.LocalGeminiNanoValidator
import com.alhaq.amniquest.core.utils.MINIMUM_ZERO_SHOT_THRESHOLD
import com.alhaq.amniquest.core.utils.evaluateZeroShotFeatures
import com.alhaq.amniquest.core.utils.extractLogitsFloatArray
import com.alhaq.amniquest.core.utils.sanitizeZeroShotQueries
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
import java.io.File
import java.io.FileOutputStream
import java.nio.IntBuffer
import java.nio.LongBuffer
import javax.inject.Inject
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

    private var modelId: String? = null
    private var isOnlineInferencing = false

    private val client = TaskValidationClient()
    private val localNano = LocalGeminiNanoValidator(context)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadModel(forEvaluation = false)
        }
    }

    fun setAiSnap() {
        aiQuest = json.decodeFromString<AiSnap>(commonQuestInfo.quest_json)
    }

    fun onAiSnapQuestDone() {
        saveMarkedQuestToDb()
        isCameraScreen.value = false
    }

    fun loadModel(forEvaluation: Boolean = false): Boolean {
        return try {
            val settingsSp = context.getSharedPreferences("private_settings", Context.MODE_PRIVATE)
            val engine = settingsSp.getString("validation_engine", "cloud") ?: "cloud"

            if (engine != "local") {
                isOnlineInferencing = true
                return true
            }

            val sp = context.getSharedPreferences("models", Context.MODE_PRIVATE)
            val currentSelectedModel = sp.getString("selected_one_shot_model", "online") ?: "online"

            val tokenizerFile = File(context.filesDir, "tokenizer.model")
            if (!tokenizerFile.exists()) {
                FileDownloadWorker.enqueueTokenizerDownload(context)
            }

            val modelIdToLoad = if (currentSelectedModel == "online") {
                val downloadingId = sp.getString("downloading", null)
                val files = context.filesDir.listFiles()
                val onnxFile = files?.find { it.name.endsWith(".onnx") }
                when {
                    onnxFile != null -> {
                        val foundId = onnxFile.nameWithoutExtension
                        sp.edit(commit = true) {
                            putString("selected_one_shot_model", foundId)
                        }
                        foundId
                    }
                    !downloadingId.isNullOrBlank() -> downloadingId
                    else -> {
                        isModelDownloaded.value = false
                        if (forEvaluation) {
                            val reasonMsg = if (sp.contains("downloading")) {
                                "Please wait until the model fully downloads"
                            } else {
                                "Model not found. Please click the model icon in the top right to download it"
                            }
                            results.value = TaskValidationClient.ValidationResult(
                                isValid = false,
                                reason = reasonMsg
                            )
                            currentStep.value = EvaluationStep.COMPLETED
                        }
                        isModelLoaded = false
                        return false
                    }
                }
            } else {
                currentSelectedModel
            }

            val modelFile = File(context.filesDir, "$modelIdToLoad.onnx")
            if (!modelFile.exists()) {
                isModelDownloaded.value = false
                if (forEvaluation) {
                    val reasonMsg = if (sp.contains("downloading")) {
                        "Please wait until the model fully downloads"
                    } else {
                        "Model not found. Please click the model icon in the top right to download it"
                    }
                    results.value = TaskValidationClient.ValidationResult(
                        isValid = false,
                        reason = reasonMsg
                    )
                    currentStep.value = EvaluationStep.COMPLETED
                }
                isModelLoaded = false
                return false
            }

            if (!tokenizerFile.exists()) {
                if (forEvaluation) {
                    results.value = TaskValidationClient.ValidationResult(
                        isValid = false,
                        reason = "Tokenizer model is downloading in the background. Please retry in a few seconds."
                    )
                    currentStep.value = EvaluationStep.COMPLETED
                }
                isModelLoaded = false
                return false
            }

            if (isModelLoaded && modelSession != null && modelId == modelIdToLoad) {
                isOnlineInferencing = false
                return true
            }

            if (forEvaluation) {
                currentStep.value = EvaluationStep.CHECKING_MODEL
            }
            if (env == null) {
                env = OrtEnvironment.getEnvironment()
            }
            modelId = modelIdToLoad
            isOnlineInferencing = false

            Log.d("AiSnapQuestViewVM", "Starting to load model $modelId")
            if (forEvaluation) {
                currentStep.value = EvaluationStep.LOADING_MODEL
            }

            try {
                modelSession?.close()
            } catch (_: Throwable) {}
            modelSession = null

            var createdSession: OrtSession? = null
            var usingNnapi = false
            SessionOptions().use { nnapiOpts ->
                val nnapiAdded = try {
                    nnapiOpts.addNnapi()
                    true
                } catch (e: Throwable) {
                    Log.w("AiSnapQuestViewVM", "NNAPI not available: ${e.message}")
                    false
                }
                nnapiOpts.setExecutionMode(SessionOptions.ExecutionMode.SEQUENTIAL)
                nnapiOpts.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
                if (nnapiAdded) {
                    try {
                        createdSession = env!!.createSession(modelFile.absolutePath, nnapiOpts)
                        usingNnapi = true
                    } catch (e: Throwable) {
                        Log.w("AiSnapQuestViewVM", "Failed to create session with NNAPI, falling back to CPU: ${e.message}")
                    }
                }
            }

            if (createdSession == null) {
                SessionOptions().use { cpuOpts ->
                    cpuOpts.setExecutionMode(SessionOptions.ExecutionMode.SEQUENTIAL)
                    cpuOpts.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
                    createdSession = env!!.createSession(modelFile.absolutePath, cpuOpts)
                    usingNnapi = false
                }
            }

            modelSession = createdSession
            isSessionUsingNnapi = usingNnapi
            isModelDownloaded.value = true
            isModelLoaded = true
            return true
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            if (forEvaluation) {
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Failed to load model: ${e.message}"
                )
                currentStep.value = EvaluationStep.COMPLETED
            }
            isModelLoaded = false
            false
        }
    }

    private fun recreateCpuSession(environment: OrtEnvironment): OrtSession {
        try {
            modelSession?.close()
        } catch (_: Throwable) {}
        val modelFile = File(context.filesDir, "$modelId.onnx")
        val cpuSession = SessionOptions().use { cpuOpts ->
            cpuOpts.setExecutionMode(SessionOptions.ExecutionMode.SEQUENTIAL)
            cpuOpts.setOptimizationLevel(SessionOptions.OptLevel.ALL_OPT)
            environment.createSession(modelFile.absolutePath, cpuOpts)
        }
        modelSession = cpuSession
        isSessionUsingNnapi = false
        return cpuSession
    }

    fun evaluateQuest(onEvaluationComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            resetResults()
            val settingsSp = context.getSharedPreferences("private_settings", Context.MODE_PRIVATE)
            val engine = settingsSp.getString("validation_engine", "cloud") ?: "cloud"

            if (engine == "local") {
                if (!loadModel(forEvaluation = true)) return@launch
                runOfflineInference(onEvaluationComplete)
            } else {
                runOnlineInference(onEvaluationComplete)
            }
        }
    }

    private suspend fun runOnlineInference(onEvaluationComplete: () -> Unit) {
        try {
            currentStep.value = EvaluationStep.INITIALIZING
            currentStep.value = EvaluationStep.LOADING_MODEL
            val photoFile = File(context.filesDir, AI_SNAP_PIC)
            if (!photoFile.exists()) {
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Image file not found."
                )
                currentStep.value = EvaluationStep.COMPLETED
                return
            }

            val compressedFile = resizeAndCompressImage(photoFile, 1080, 50)

            val settingsSp = context.getSharedPreferences("private_settings", Context.MODE_PRIVATE)
            val engine = settingsSp.getString("validation_engine", "cloud") ?: "cloud"

            if (engine == "gemini_api") {
                val geminiKey = settingsSp.getString("gemini_api_key", null)
                if (geminiKey.isNullOrBlank()) {
                    results.value = TaskValidationClient.ValidationResult(
                        isValid = false,
                        reason = "Private Gemini API key is missing. Please configure it in your Profile settings."
                    )
                    currentStep.value = EvaluationStep.COMPLETED
                    return
                }
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
            } else { // "cloud"
                val token = if (userRepository.userInfo.isAnonymous) {
                    ""
                } else {
                    Supabase.supabase.auth.currentAccessTokenOrNull()?.toString() ?: ""
                }

                if (token.isEmpty()) {
                    results.value = TaskValidationClient.ValidationResult(
                        isValid = false,
                        reason = "Authentication required. Please log in or configure a private Gemini API Key / Local AI."
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
            }

            val allSteps = EvaluationStep.entries
            var currentStepInt = 0
            while (results.value == null) {
                delay(Random.nextInt(500, 2000).toLong())
                currentStep.value = EvaluationStep.valueOf(allSteps[currentStepInt].name)
                if (currentStepInt != EvaluationStep.EVALUATING.ordinal) currentStepInt++
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Online evaluation failed: ${e.message}"
            )
            currentStep.value = EvaluationStep.COMPLETED
        }
    }

    fun resetResults() {
        isAiEvaluating.value = true
        results.value = null
        error.value = null
        currentStep.value = EvaluationStep.INITIALIZING
    }

    private suspend fun runOfflineInference(onEvaluationComplete: () -> Unit) {
        try {
            currentStep.value = EvaluationStep.INITIALIZING

            val photoFile = File(context.filesDir, AI_SNAP_PIC)
            currentStep.value = EvaluationStep.LOADING_IMAGE

            if (!photoFile.exists()) {
                val msg = "Image file not found at ${photoFile.absolutePath}"
                Log.d("AiSnapQuestViewVM", msg)
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Image file not found."
                )
                currentStep.value = EvaluationStep.COMPLETED
                return
            }

            val bitmap = getBitmapFromPath(photoFile.absolutePath)
            if (bitmap == null) {
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Failed to load bitmap from ${photoFile.absolutePath}"
                )
                currentStep.value = EvaluationStep.COMPLETED
                return
            }

            currentStep.value = EvaluationStep.PREPROCESSING

            val queries = sanitizeZeroShotQueries(aiQuest.features, aiQuest.taskDescription)
            if (queries.isEmpty()) {
                bitmap.recycle()
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Quest task description and features are empty."
                )
                currentStep.value = EvaluationStep.COMPLETED
                return
            }

            currentStep.value = EvaluationStep.TOKENIZING

            val tokenIdsList = try {
                tokenizeText(context, queries)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                bitmap.recycle()
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Tokenization failed: ${e.message}"
                )
                currentStep.value = EvaluationStep.COMPLETED
                return
            }

            val initialSession = modelSession
            val environment = env
            if (initialSession == null || environment == null) {
                bitmap.recycle()
                results.value = TaskValidationClient.ValidationResult(
                    isValid = false,
                    reason = "Model session is not initialized"
                )
                currentStep.value = EvaluationStep.COMPLETED
                return
            }

            val maxLength = 64
            val padTokenId = 0L
            val eosTokenId = 1L
            val paddedTokenIdsList = tokenIdsList.map { padTokenIds(it, maxLength, padTokenId, eosTokenId) }
            val flatTokenIds = paddedTokenIdsList.flatMap { it.asList() }.toLongArray()

            val floatBuffer = preprocessBitmapToFloatBuffer(bitmap)
            bitmap.recycle()

            val imageShape = longArrayOf(1, 3, 224, 224)
            val pixelValuesType = (initialSession.inputInfo["pixel_values"]?.info as? TensorInfo)?.type
            val imageTensor = when (pixelValuesType) {
                OnnxJavaType.FLOAT16 -> {
                    val fp16Buf = Fp16Conversions.convertFloatBufferToFp16Buffer(floatBuffer)
                    OnnxTensor.createTensor(environment, fp16Buf, imageShape, OnnxJavaType.FLOAT16)
                }
                OnnxJavaType.BFLOAT16 -> {
                    val bf16Buf = Fp16Conversions.convertFloatBufferToBf16Buffer(floatBuffer)
                    OnnxTensor.createTensor(environment, bf16Buf, imageShape, OnnxJavaType.BFLOAT16)
                }
                else -> {
                    OnnxTensor.createTensor(environment, floatBuffer, imageShape)
                }
            }

            val textShape = longArrayOf(paddedTokenIdsList.size.toLong(), maxLength.toLong())
            val inputIdsType = (initialSession.inputInfo["input_ids"]?.info as? TensorInfo)?.type
            val textTensor = if (inputIdsType == OnnxJavaType.INT32) {
                val intIds = IntArray(flatTokenIds.size) { flatTokenIds[it].toInt() }
                OnnxTensor.createTensor(environment, IntBuffer.wrap(intIds), textShape)
            } else {
                OnnxTensor.createTensor(environment, LongBuffer.wrap(flatTokenIds), textShape)
            }

            val maskTensor = if (initialSession.inputNames.contains("attention_mask")) {
                val maskType = (initialSession.inputInfo["attention_mask"]?.info as? TensorInfo)?.type
                if (maskType == OnnxJavaType.INT32) {
                    val maskInts = IntArray(flatTokenIds.size) { if (flatTokenIds[it] != padTokenId) 1 else 0 }
                    OnnxTensor.createTensor(environment, IntBuffer.wrap(maskInts), textShape)
                } else {
                    val maskLongs = LongArray(flatTokenIds.size) { if (flatTokenIds[it] != padTokenId) 1L else 0L }
                    OnnxTensor.createTensor(environment, LongBuffer.wrap(maskLongs), textShape)
                }
            } else {
                null
            }

            currentStep.value = EvaluationStep.EVALUATING

            val inputs = mutableMapOf<String, OnnxTensor>(
                "pixel_values" to imageTensor,
                "input_ids" to textTensor
            )
            if (maskTensor != null) {
                inputs["attention_mask"] = maskTensor
            }

            val logitsArray: FloatArray = try {
                val resultOutput = try {
                    initialSession.run(inputs)
                } catch (runError: Throwable) {
                    if (isSessionUsingNnapi) {
                        Log.w("AiSnapQuestViewVM", "NNAPI execution failed at runtime, recreating session on CPU: ${runError.message}")
                        val cpuSession = recreateCpuSession(environment)
                        cpuSession.run(inputs)
                    } else {
                        throw runError
                    }
                }

                resultOutput.use { output ->
                    val opt = output.get("logits_per_image")
                    val namedTensor = if (opt.isPresent) opt.get() as? OnnxTensor else null
                    val logitsTensor = namedTensor ?: (output.get(0) as? OnnxTensor)
                        ?: throw IllegalStateException("Model output logits tensor is null")
                    extractLogitsFloatArray(logitsTensor)
                }
            } finally {
                imageTensor.close()
                textTensor.close()
                maskTensor?.close()
            }

            val explicitFeaturesCount = aiQuest.features.count { it.isNotBlank() }
            val (detectedFeatures, fallbackResult) = evaluateZeroShotFeatures(
                queries = queries,
                hasExplicitFeatures = explicitFeaturesCount > 0,
                taskDescription = aiQuest.taskDescription,
                logitsArray = logitsArray,
                threshold = MINIMUM_ZERO_SHOT_THRESHOLD
            )

            // Local Gemini Nano reasoning pipeline integration (when required visual features are detected)
            if (fallbackResult.isValid && detectedFeatures.isNotEmpty() && localNano.isAvailable()) {
                val nanoResult = localNano.validateTaskLocally(aiQuest.taskDescription, detectedFeatures)
                if (nanoResult.isValid || !nanoResult.reason.startsWith("On-device AI reasoning error")) {
                    results.value = nanoResult
                    currentStep.value = EvaluationStep.COMPLETED
                    if (nanoResult.isValid) {
                        onEvaluationComplete()
                    }
                    return
                }
                Log.w("AiSnapQuestViewVM", "Gemini Nano execution failed, falling back to SigLIP 2 zero-shot verification: ${nanoResult.reason}")
            }

            // Fallback: Default SigLIP verification logic
            results.value = fallbackResult
            currentStep.value = EvaluationStep.COMPLETED

            if (fallbackResult.isValid) {
                onEvaluationComplete()
            }

        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            error.value = "Evaluation failed: ${e.message}"
            results.value = TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Evaluation failed: ${e.message}"
            )
            currentStep.value = EvaluationStep.COMPLETED
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            modelSession?.close()
            env?.close()
            modelSession = null
            env = null
            isModelLoaded = false
            isSessionUsingNnapi = false
        } catch (e: Throwable) {
            Log.e("AiEvaluation", "Failed to close resources", e)
        }
    }
}

fun resizeAndCompressImage(file: File, maxSize: Int = 1080, quality: Int = 70): File {
    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        ?: return file
    if (bitmap.width <= 0 || bitmap.height <= 0) {
        bitmap.recycle()
        return file
    }

    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
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
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
        out.flush()
    }
    if (scaledBitmap != bitmap) {
        scaledBitmap.recycle()
    }
    bitmap.recycle()

    return compressedFile
}
