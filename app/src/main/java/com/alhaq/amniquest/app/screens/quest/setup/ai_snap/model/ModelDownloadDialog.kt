package com.alhaq.amniquest.app.screens.quest.setup.ai_snap.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.alhaq.amniquest.BuildConfig
import com.alhaq.amniquest.backed.fetchUrlContent
import com.alhaq.amniquest.core.workers.FileDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

const val SIGLIP2_HF_BASE =
    "https://huggingface.co/onnx-community/siglip2-base-patch16-224-ONNX/resolve/main"
const val SIGLIP2_TOKENIZER_URL = "$SIGLIP2_HF_BASE/tokenizer.model"
const val SIGLIP2_Q4_URL = "$SIGLIP2_HF_BASE/onnx/model_q4.onnx"
const val SIGLIP2_INT8_URL = "$SIGLIP2_HF_BASE/onnx/model_int8.onnx"
const val SIGLIP2_UINT8_URL = "$SIGLIP2_HF_BASE/onnx/model_uint8.onnx"
const val SIGLIP2_FP16_URL = "$SIGLIP2_HF_BASE/onnx/model_fp16.onnx"
const val SIGLIP2_Q4F16_URL = "$SIGLIP2_HF_BASE/onnx/model_q4f16.onnx"

@kotlinx.serialization.Serializable
data class DownloadableModel(
    val sizeMB: Int,
    val accuracy: String,
    val id: String,
    val url: String,
    val version: String,
    val isRecommended: Boolean = false,
    val isOnline: Boolean = false
)

val DEFAULT_MODEL_CATALOG = listOf(
    DownloadableModel(
        id = "online",
        url = "",
        sizeMB = 0,
        accuracy = "HIGHEST",
        version = "1.0.0",
        isRecommended = true,
        isOnline = true
    ),
    DownloadableModel(
        id = "SIGLIP2_Q4",
        url = SIGLIP2_Q4_URL,
        sizeMB = 252,
        accuracy = "Fast (Q4)",
        version = "1.0.0",
        isRecommended = false,
        isOnline = false
    ),
    DownloadableModel(
        id = "SIGLIP2_UINT8",
        url = SIGLIP2_UINT8_URL,
        sizeMB = 378,
        accuracy = "Standard",
        version = "1.0.0",
        isRecommended = false,
        isOnline = false
    ),
    DownloadableModel(
        id = "SIGLIP2_INT8",
        url = SIGLIP2_INT8_URL,
        sizeMB = 378,
        accuracy = "Standard (INT8)",
        version = "1.0.0",
        isRecommended = false,
        isOnline = false
    ),
    DownloadableModel(
        id = "SIGLIP2_Q4F16",
        url = SIGLIP2_Q4F16_URL,
        sizeMB = 497,
        accuracy = "Balanced (Q4F16)",
        version = "1.0.0",
        isRecommended = false,
        isOnline = false
    ),
    DownloadableModel(
        id = "SIGLIP2_FP16",
        url = SIGLIP2_FP16_URL,
        sizeMB = 751,
        accuracy = "Medium",
        version = "1.0.0",
        isRecommended = false,
        isOnline = false
    ),
    DownloadableModel(
        id = "SIGLIP2_FULL",
        url = "$SIGLIP2_HF_BASE/onnx/model.onnx",
        sizeMB = 1500,
        accuracy = "High",
        version = "1.0.0",
        isRecommended = false,
        isOnline = false
    )
)

internal fun resolveModelCatalog(remoteJson: String?): List<DownloadableModel> {
    if (remoteJson.isNullOrBlank()) return DEFAULT_MODEL_CATALOG
    return try {
        val parsed = Json.decodeFromString(
            ListSerializer(DownloadableModel.serializer()),
            remoteJson
        )
        if (parsed.isEmpty()) return DEFAULT_MODEL_CATALOG
        val byId = LinkedHashMap<String, DownloadableModel>()
        for (model in parsed) {
            val sanitized = sanitizeDownloadableModel(model)
            byId[sanitized.id] = sanitized
        }
        for (model in DEFAULT_MODEL_CATALOG) {
            val sanitized = sanitizeDownloadableModel(model)
            if (!byId.containsKey(sanitized.id)) {
                byId[sanitized.id] = sanitized
            }
        }
        byId.values.toList()
    } catch (e: Exception) {
        try {
            Log.w("ModelDownloadDialog", "Failed to parse remote catalog, using offline fallback: ${e.message}")
        } catch (_: Throwable) {}
        DEFAULT_MODEL_CATALOG
    }
}

internal fun resolveActiveLocalModelId(
    selectedModel: String?,
    downloadingModel: String?,
    downloadedModelIdsOnDisk: Set<String>
): String? {
    if (!selectedModel.isNullOrBlank() && selectedModel != "online" && downloadedModelIdsOnDisk.contains(selectedModel)) {
        return selectedModel
    }
    if (!downloadingModel.isNullOrBlank() && downloadingModel != "online") {
        return downloadingModel
    }
    if (!selectedModel.isNullOrBlank() && selectedModel != "online") {
        return downloadedModelIdsOnDisk.firstOrNull() ?: selectedModel
    }
    return downloadedModelIdsOnDisk.firstOrNull()
}

private fun ensureTokenizerDownloaded(context: Context) {
    val modelSp = context.getSharedPreferences("models", Context.MODE_PRIVATE)
    val isTokenizerDownloaded = modelSp.getBoolean("is_downloaded_tokenizer", false)
    val tokenizerFile = File(context.filesDir, "tokenizer.model")
    if (!isTokenizerDownloaded || !tokenizerFile.exists()) {
        FileDownloadWorker.enqueueTokenizerDownload(context)
    }
}

fun ensureTokenizerQueued(context: Context) {
    ensureTokenizerDownloaded(context)
}

fun sanitizeDownloadableModel(model: DownloadableModel): DownloadableModel {
    if (model.isOnline || model.id.equals("online", ignoreCase = true)) {
        return model.copy(isOnline = true)
    }
    val lowerId = model.id.lowercase()
    val lowerUrl = model.url.lowercase()

    val isUnquantizedFp32 = lowerUrl.endsWith("/model.onnx") ||
        lowerUrl.contains("model.onnx?") ||
        lowerId.contains("fp32") ||
        model.sizeMB > 1000
    val isNonSiglip2 = !lowerUrl.contains("siglip2-base-patch16-224-onnx")

    if (!isUnquantizedFp32 && !isNonSiglip2) {
        return model
    }

    return when {
        lowerId.contains("small") || lowerId.contains("q4") || lowerId.contains("tiny") -> model.copy(
            url = SIGLIP2_Q4_URL,
            sizeMB = if (model.sizeMB in 50..350) model.sizeMB else 252
        )
        lowerId.contains("medium") || lowerId.contains("int8") -> model.copy(
            url = SIGLIP2_INT8_URL,
            sizeMB = if (model.sizeMB in 200..500) model.sizeMB else 380
        )
        lowerId.contains("fp16") -> model.copy(
            url = SIGLIP2_FP16_URL,
            sizeMB = if (model.sizeMB in 500..900) model.sizeMB else 751
        )
        else -> model.copy(
            url = SIGLIP2_UINT8_URL,
            sizeMB = if (model.sizeMB in 250..600) model.sizeMB else 378
        )
    }
}

@Composable
fun ModelDownloadDialog(
    allowSkipping: Boolean = true,
    modelDownloadDialogVisible: MutableState<Boolean>,
) {
    val context = LocalContext.current
    val sp = remember { context.getSharedPreferences("models", Context.MODE_PRIVATE) }
    val privateSp = remember { context.getSharedPreferences("private_settings", Context.MODE_PRIVATE) }
    var currentModel by remember { mutableStateOf(sp.getString("selected_one_shot_model", null)) }
    var isModelDownloading by remember { mutableStateOf(sp.contains("downloading")) }
    var prefsRevision by remember { mutableIntStateOf(0) }
    var modelStates by remember {
        mutableStateOf(DEFAULT_MODEL_CATALOG.map { mutableStateOf(it) })
    }

    DisposableEffect(sp) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
            currentModel = prefs.getString("selected_one_shot_model", null)
            isModelDownloading = prefs.contains("downloading")
            prefsRevision++
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sp.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(Unit) {
        val existingModel = sp.getString("selected_one_shot_model", null)
        if (existingModel != null && (existingModel == "model" || existingModel == "fp32" || existingModel == "full_fp32")) {
            val migrated = "SIGLIP2_FULL"
            sp.edit(commit = true) {
                putString("selected_one_shot_model", migrated)
            }
            currentModel = migrated
        } else if (existingModel == null && !BuildConfig.IS_FDROID) {
            currentModel = "online"
            sp.edit(commit = true) {
                putString("selected_one_shot_model", "online")
            }
        }
        val engine = privateSp.getString("validation_engine", "cloud") ?: "cloud"
        if (!allowSkipping && currentModel == null && engine == "local") {
            modelDownloadDialogVisible.value = true
        }
    }

    LaunchedEffect(modelDownloadDialogVisible.value) {
        val engine = privateSp.getString("validation_engine", "cloud") ?: "cloud"
        val downloadedOnDisk = context.filesDir.listFiles()
            ?.filter { it.name.endsWith(".onnx") }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?: emptySet()

        if (engine == "local") {
            val resolvedLocal = resolveActiveLocalModelId(
                selectedModel = sp.getString("selected_one_shot_model", null),
                downloadingModel = sp.getString("downloading", null),
                downloadedModelIdsOnDisk = downloadedOnDisk
            )
            if (resolvedLocal != null && sp.getString("selected_one_shot_model", null) == "online") {
                sp.edit(commit = true) {
                    putString("selected_one_shot_model", resolvedLocal)
                }
            }
            currentModel = resolvedLocal
        } else {
            currentModel = sp.getString("selected_one_shot_model", "online") ?: "online"
        }

        isModelDownloading = sp.contains("downloading")
        prefsRevision++

        if (!modelDownloadDialogVisible.value) return@LaunchedEffect

        // Reconcile stale "downloading" flag if WorkManager job is no longer active
        val downloadingId = sp.getString("downloading", null)
        if (!downloadingId.isNullOrBlank()) {
            val isStillActive = withContext(Dispatchers.IO) {
                try {
                    val infos = WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWork("download_${downloadingId}.onnx")
                        .get()
                    infos.any { info ->
                        info.state == WorkInfo.State.RUNNING ||
                            info.state == WorkInfo.State.ENQUEUED ||
                            info.state == WorkInfo.State.BLOCKED
                    }
                } catch (_: Throwable) {
                    false
                }
            }
            if (!isStillActive) {
                val targetExists = File(context.filesDir, "${downloadingId}.onnx").exists()
                sp.edit(commit = true) {
                    remove("downloading")
                    if (targetExists) {
                        putBoolean("is_downloaded_$downloadingId", true)
                    }
                }
                isModelDownloading = false
                prefsRevision++
            }
        }

        val remoteJson = try {
            fetchUrlContent("https://raw.githubusercontent.com/QuestPhone/models/refs/heads/main/model_data.json")
        } catch (e: Exception) {
            Log.w("ModelDownloadDialog", "Failed to fetch remote catalog: ${e.message}")
            null
        }

        val parsedCatalog = resolveModelCatalog(remoteJson)
        modelStates = parsedCatalog.map { mutableStateOf(it) }

        // Check if any downloaded model has a version mismatch
        for (modelState in modelStates) {
            val model = modelState.value
            val localVersion = sp.getString("version_${model.id}", null)
            val isDownloaded = sp.getBoolean("is_downloaded_${model.id}", false)
            if (!model.isOnline && isDownloaded && localVersion != null && localVersion != model.version) {
                context.deleteFile("${model.id}.onnx")
                context.deleteFile("${model.id}.onnx.part")
                sp.edit(commit = true) {
                    remove("is_downloaded_${model.id}")
                    remove("version_${model.id}")
                    putString("downloading", model.id)
                }
                Toast.makeText(context, "Model ${model.id} is outdated. Re-downloading...", Toast.LENGTH_SHORT).show()

                isModelDownloading = true
                val targetModel = sanitizeDownloadableModel(model)
                val inputData = Data.Builder()
                    .putString(FileDownloadWorker.KEY_URL, targetModel.url)
                    .putString(FileDownloadWorker.KEY_FILE_NAME, "${targetModel.id}.onnx")
                    .putString(FileDownloadWorker.KEY_MODEL_ID, targetModel.id)
                    .putString(FileDownloadWorker.KEY_MODEL_VERSION, targetModel.version)
                    .putBoolean(FileDownloadWorker.KEY_IS_ONE_SHOT, true)
                    .build()

                val downloadWork = OneTimeWorkRequestBuilder<FileDownloadWorker>()
                    .setInputData(inputData)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "download_${model.id}.onnx",
                    ExistingWorkPolicy.KEEP,
                    downloadWork
                )
                ensureTokenizerDownloaded(context)
            }
        }
    }

    fun onDismiss() {
        if (currentModel == null && !allowSkipping && !isModelDownloading) {
            Toast.makeText(context, "Please select a model to perform this quest", Toast.LENGTH_SHORT).show()
        }
        modelDownloadDialogVisible.value = false
    }

    if (modelDownloadDialogVisible.value) {
        // Read prefsRevision so Compose recomposes items when SharedPreferences changes
        val revision = prefsRevision
        Dialog(onDismissRequest = { onDismiss() }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Download Model", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                    Text(
                        "On-device AI Snap uses quantized SigLIP 2 ONNX models for zero-shot object detection paired with local Gemini Nano reasoning. Select and download an offline model below.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                    if (isModelDownloading) {
                        val downloadingModelId = sp.getString("downloading", "") ?: ""
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                if (downloadingModelId.isNotEmpty()) {
                                    "Downloading $downloadingModelId model in background...\nCheck notifications for live progress."
                                } else {
                                    "Downloading model in background...\nCheck notifications for live progress."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            TextButton(
                                onClick = {
                                    if (downloadingModelId.isNotEmpty()) {
                                        WorkManager.getInstance(context)
                                            .cancelUniqueWork("download_${downloadingModelId}.onnx")
                                    }
                                    sp.edit(commit = true) {
                                        remove("downloading")
                                    }
                                    isModelDownloading = false
                                    prefsRevision++
                                    Toast.makeText(context, "Download cancelled", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Cancel Download", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        if (modelStates.isEmpty()) {
                            Text("Loading Available Models")
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f, false)) {
                            items(modelStates) { modelState ->
                                val model = modelState.value
                                val isFileDownloaded = !model.isOnline &&
                                    revision >= 0 &&
                                    sp.getBoolean("is_downloaded_${model.id}", false) &&
                                    File(context.filesDir, "${model.id}.onnx").exists()
                                val isAvailableToSelect = isFileDownloaded || model.isOnline
                                val localVersion = sp.getString("version_${model.id}", null)
                                val isSelected = currentModel == model.id && (isFileDownloaded || model.isOnline)
                                val needsUpdate = (!model.isOnline && isFileDownloaded && localVersion != null && localVersion != model.version)

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            isAvailableToSelect -> MaterialTheme.colorScheme.surfaceVariant
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        }
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isModelDownloading) {
                                            if ((isFileDownloaded && !needsUpdate) || model.isOnline) {
                                                sp.edit(commit = true) {
                                                    putString("selected_one_shot_model", model.id)
                                                }
                                                currentModel = model.id

                                                privateSp.edit().putString(
                                                    "validation_engine",
                                                    if (model.isOnline) "cloud" else "local"
                                                ).apply()

                                                if (!model.isOnline) {
                                                    ensureTokenizerDownloaded(context)
                                                }
                                                Toast.makeText(context, "AI engine switched to ${if (model.isOnline) "Cloud Server" else "Local AI (${model.id})"}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val targetModel = sanitizeDownloadableModel(model)
                                                sp.edit(commit = true) {
                                                    putString("downloading", targetModel.id)
                                                    putString("selected_one_shot_model", targetModel.id)
                                                }
                                                currentModel = targetModel.id
                                                privateSp.edit().putString("validation_engine", "local").apply()

                                                isModelDownloading = true
                                                val inputData = Data.Builder()
                                                    .putString(FileDownloadWorker.KEY_URL, targetModel.url)
                                                    .putString(FileDownloadWorker.KEY_FILE_NAME, "${targetModel.id}.onnx")
                                                    .putString(FileDownloadWorker.KEY_MODEL_ID, targetModel.id)
                                                    .putString(FileDownloadWorker.KEY_MODEL_VERSION, targetModel.version)
                                                    .putBoolean(FileDownloadWorker.KEY_IS_ONE_SHOT, true)
                                                    .build()

                                                val workRequest = OneTimeWorkRequestBuilder<FileDownloadWorker>()
                                                    .setInputData(inputData)
                                                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                                                    .build()

                                                WorkManager.getInstance(context).enqueueUniqueWork(
                                                    "download_${targetModel.id}.onnx",
                                                    ExistingWorkPolicy.KEEP,
                                                    workRequest
                                                )
                                                ensureTokenizerDownloaded(context)

                                                Toast.makeText(context, "Download Started", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                if (model.isRecommended) {
                                                    Text("Recommended", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Text(
                                                    model.id,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (isSelected)
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    "Accuracy: ${model.accuracy}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isSelected)
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                                Text(
                                                    text = if (model.isOnline) {
                                                        "Requires online access and cloud authentication / Gemini API Key. Best validation accuracy."
                                                    } else {
                                                        "Completely offline. Uses on-device SigLIP 2 ONNX vision model with local Gemini Nano reasoning."
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isSelected)
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                                Text(
                                                    "Version: ${model.version}" + if (needsUpdate) " (Update Available)" else "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error.takeIf { needsUpdate }
                                                        ?: MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                when {
                                                    model.isOnline -> {
                                                        Text(
                                                            "Cloud",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (isSelected)
                                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    isFileDownloaded -> {
                                                        TextButton(
                                                            onClick = {
                                                                context.deleteFile("${model.id}.onnx")
                                                                context.deleteFile("${model.id}.onnx.part")
                                                                val fallbackModel = modelStates
                                                                    .map { it.value }
                                                                    .firstOrNull { other ->
                                                                        !other.isOnline &&
                                                                            other.id != model.id &&
                                                                            sp.getBoolean("is_downloaded_${other.id}", false) &&
                                                                            File(context.filesDir, "${other.id}.onnx").exists()
                                                                    }?.id
                                                                    ?: if (!BuildConfig.IS_FDROID) "online" else null

                                                                sp.edit(commit = true) {
                                                                    remove("is_downloaded_${model.id}")
                                                                    remove("version_${model.id}")
                                                                    if (currentModel == model.id) {
                                                                        if (fallbackModel != null) {
                                                                            putString("selected_one_shot_model", fallbackModel)
                                                                        } else {
                                                                            remove("selected_one_shot_model")
                                                                        }
                                                                    }
                                                                }
                                                                if (currentModel == model.id) {
                                                                    currentModel = fallbackModel
                                                                    if (fallbackModel == "online") {
                                                                        privateSp.edit()
                                                                            .putString("validation_engine", "cloud")
                                                                            .apply()
                                                                    }
                                                                }
                                                                prefsRevision++
                                                                Toast.makeText(context, "Model deleted", Toast.LENGTH_SHORT).show()
                                                            },
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        ) {
                                                            Text("Delete", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                    else -> {
                                                        Text(
                                                            "${model.sizeMB}mb",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (isSelected)
                                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onDismiss() }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}
