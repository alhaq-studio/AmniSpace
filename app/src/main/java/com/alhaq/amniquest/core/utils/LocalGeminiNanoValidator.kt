package com.alhaq.amniquest.core.utils

import android.content.Context
import android.util.Log
import com.google.ai.edge.aicore.DownloadCallback
import com.google.ai.edge.aicore.DownloadConfig
import com.google.ai.edge.aicore.GenerationConfig
import com.google.ai.edge.aicore.GenerativeAIException
import com.google.ai.edge.aicore.GenerativeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.alhaq.amniquest.backend.TaskValidationClient

class LocalGeminiNanoValidator(private val context: Context) {

    private var generativeModel: GenerativeModel? = null
    private var isPrepared = false

    companion object {
        private const val TAG = "LocalGeminiNano"
    }

    init {
        try {
            val downloadCallback = object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) {
                    Log.i(TAG, "Gemini Nano download started. Total bytes to download: $bytesToDownload")
                }

                override fun onDownloadProgress(progress: Long) {
                    Log.i(TAG, "Gemini Nano download progress: $progress")
                }

                override fun onDownloadCompleted() {
                    Log.i(TAG, "Gemini Nano download completed")
                    CoroutineScope(Dispatchers.IO).launch {
                        prepareEngine()
                    }
                }

                override fun onDownloadFailed(failureStatus: String, e: GenerativeAIException) {
                    Log.e(TAG, "Gemini Nano download failed: $failureStatus", e)
                }
            }

            val downloadConfig = DownloadConfig(downloadCallback)

            val generationConfig = GenerationConfig.Builder().build()

            generativeModel = GenerativeModel(
                generationConfig = generationConfig,
                downloadConfig = downloadConfig
            )
            CoroutineScope(Dispatchers.IO).launch {
                prepareEngine()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "AICore / Gemini Nano not supported on this device: ${e.message}")
            generativeModel = null
        }
    }

    private suspend fun prepareEngine() {
        val model = generativeModel ?: return
        try {
            model.prepareInferenceEngine()
            isPrepared = true
            Log.i(TAG, "Gemini Nano inference engine prepared successfully")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to prepare local inference engine: ${e.message}")
        }
    }

    fun isAvailable(): Boolean {
        return generativeModel != null && isPrepared
    }

    suspend fun validateTaskLocally(
        taskDescription: String,
        detectedFeatures: List<String>
    ): TaskValidationClient.ValidationResult {
        if (detectedFeatures.isEmpty()) {
            return TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "No matching visual features were detected in the image."
            )
        }
        val model = generativeModel
        if (model == null || !isPrepared) {
            return TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "Local Gemini Nano is not active or not prepared on this device."
            )
        }

        val prompt = """
            You are a supportive, encouraging local AI Habit Coach.
            The user's goal description: "$taskDescription"
            The objects/features detected in their environment: ${detectedFeatures.joinToString(", ")}.
            
            Determine if the user successfully completed their goal based on the detected objects.
            Respond exactly with a JSON object:
            {
               "is_valid": true/false,
               "reason": "encouraging feedback or specific instructions on what is missing"
            }
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            val responseText = response.text ?: ""
            parseNanoValidationResponse(responseText)
        } catch (e: Throwable) {
            Log.e(TAG, "Local reasoning execution failed", e)
            TaskValidationClient.ValidationResult(
                isValid = false,
                reason = "On-device AI reasoning error: ${e.message}"
            )
        }
    }
}

internal fun parseNanoValidationResponse(responseText: String): TaskValidationClient.ValidationResult {
    val isValidMatch = "\"is_valid\"\\s*:\\s*(true|false)".toRegex(RegexOption.IGNORE_CASE).find(responseText)
        ?: return TaskValidationClient.ValidationResult(
            isValid = false,
            reason = "On-device AI reasoning error: Malformed response from Gemini Nano"
        )

    val isValid = isValidMatch.groupValues[1].equals("true", ignoreCase = true)

    val quotedReasonRegex = "\"reason\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex(RegexOption.DOT_MATCHES_ALL)
    val quotedMatch = quotedReasonRegex.find(responseText)
    val parsedReason = if (quotedMatch != null) {
        quotedMatch.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\\", "\\")
            .trim()
    } else {
        val reasonStart = responseText.indexOf("\"reason\"")
        if (reasonStart != -1) {
            responseText.substring(reasonStart + 8)
                .substringAfter(":")
                .substringBefore(",")
                .substringBefore("}")
                .trim('"', ' ', '\n', '\r', ',')
        } else {
            ""
        }
    }

    val reason = parsedReason.ifBlank {
        if (isValid) {
            "Goal validated successfully!"
        } else {
            "Could not verify goal from detected environment features."
        }
    }
    return TaskValidationClient.ValidationResult(isValid, reason)
}

