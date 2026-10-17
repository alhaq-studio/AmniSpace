package neth.iecal.questphone.app.screens.launcher.widget.picker

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neth.iecal.questphone.backed.repositories.AppWidgetConfig
import neth.iecal.questphone.backed.repositories.AppWidgetConfigDao
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.roundToInt

data class PersistedWidget(
    val widgetId: Int,
    val widgetInfo: AppWidgetProviderInfo,
    val config: AppWidgetConfig
)

data class AvailableWidgetItem(
    val providerInfo: AppWidgetProviderInfo,
    val label: String,
    val description: String?,
    val previewImage: Drawable?,
    val icon: Drawable?,
    val defaultWidthDp: Int,
    val defaultHeightDp: Int,
    val cellsWidth: Int,
    val cellsHeight: Int,
    val hasConfiguration: Boolean,
    val isConfigurationOptional: Boolean
)

data class AvailableWidgetGroup(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    val widgets: List<AvailableWidgetItem>
)

data class WidgetUiState(
    val widgets: List<PersistedWidget> = emptyList(),
    val availableWidgetGroups: List<AvailableWidgetGroup> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingCatalog: Boolean = false,
    val error: String? = null,
    val refreshTrigger: Int = 0
)

@HiltViewModel
class WidgetViewModel @Inject constructor(
    application: Application,
    val repository: AppWidgetConfigDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WidgetUiState())
    val uiState: StateFlow<WidgetUiState> = _uiState.asStateFlow()

    private val appWidgetManager = AppWidgetManager.getInstance(application)

    init {
        loadPersistedWidgets()
        loadAvailableWidgets()
    }

    fun loadPersistedWidgets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val appContext = getApplication<Application>()
                val displayMetrics = appContext.resources.displayMetrics
                val density = displayMetrics.density.coerceAtLeast(1f)
                val maxScreenWidthDp = ((displayMetrics.widthPixels / density).roundToInt() - 32).coerceAtLeast(240)

                val configs = withContext(Dispatchers.IO) { repository.getAllConfigs() }
                val widgets = mutableListOf<PersistedWidget>()

                configs.forEach { config ->
                    val widgetInfo = appWidgetManager.getAppWidgetInfo(config.widgetId)
                    if (widgetInfo != null) {
                        val (defaultW, defaultH) = calculateDefaultWidgetSizeDp(appContext, widgetInfo)
                        val currentW = config.width ?: defaultW
                        val currentH = config.height

                        // Normalize configs that previously stored raw pixels or 0dp dimensions
                        val normalizedW = when {
                            currentW <= 50 -> defaultW
                            currentW > maxScreenWidthDp -> maxScreenWidthDp
                            else -> currentW
                        }
                        val normalizedH = when {
                            currentH <= 40 -> defaultH
                            currentH > 550 -> (currentH / density).roundToInt().coerceIn(80, 420)
                            else -> currentH
                        }

                        val finalConfig = if (normalizedW != config.width || normalizedH != config.height) {
                            config.copy(width = normalizedW, height = normalizedH).also { updated ->
                                withContext(Dispatchers.IO) { repository.insertConfig(updated) }
                            }
                        } else {
                            config
                        }

                        widgets.add(
                            PersistedWidget(
                                widgetId = config.widgetId,
                                widgetInfo = widgetInfo,
                                config = finalConfig
                            )
                        )
                    } else {
                        withContext(Dispatchers.IO) { repository.deleteConfig(config) }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    widgets = widgets,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load widgets: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun loadAvailableWidgets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCatalog = true)
            try {
                val appContext = getApplication<Application>()
                val groups = withContext(Dispatchers.IO) {
                    val pm = appContext.packageManager
                    val densityDpi = appContext.resources.displayMetrics.densityDpi
                    val userManager = appContext.getSystemService(Context.USER_SERVICE) as? UserManager

                    val allProviders = LinkedHashMap<String, AppWidgetProviderInfo>()

                    // Gather providers across all profiles and packages
                    val profiles = userManager?.userProfiles.orEmpty()
                    if (profiles.isNotEmpty()) {
                        for (profile in profiles) {
                            runCatching {
                                appWidgetManager.getInstalledProvidersForProfile(profile).forEach { info ->
                                    val key = "${info.provider.flattenToString()}_${info.profile.hashCode()}"
                                    allProviders[key] = info
                                }
                            }
                        }
                    }

                    // Standard getInstalledProviders
                    runCatching {
                        appWidgetManager.installedProviders.forEach { info ->
                            val key = "${info.provider.flattenToString()}_${info.profile.hashCode()}"
                            allProviders[key] = info
                        }
                    }

                    // Also query any package declaring APPWIDGET_UPDATE receivers
                    runCatching {
                        val receivers = pm.queryBroadcastReceivers(
                            android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE),
                            0
                        )
                        val packages = receivers.mapNotNull { it.activityInfo?.packageName }.toSet()
                        for (pkg in packages) {
                            if (profiles.isNotEmpty()) {
                                for (profile in profiles) {
                                    runCatching {
                                        appWidgetManager.getInstalledProvidersForPackage(pkg, profile).forEach { info ->
                                            val key = "${info.provider.flattenToString()}_${info.profile.hashCode()}"
                                            allProviders[key] = info
                                        }
                                    }
                                }
                            } else {
                                runCatching {
                                    appWidgetManager.getInstalledProvidersForPackage(pkg, null).forEach { info ->
                                        val key = "${info.provider.flattenToString()}_${info.profile.hashCode()}"
                                        allProviders[key] = info
                                    }
                                }
                            }
                        }
                    }

                    val byPackage = allProviders.values.groupBy { it.provider.packageName }

                    byPackage.mapNotNull { (pkgName, providerList) ->
                        val appInfo = runCatching { pm.getApplicationInfo(pkgName, 0) }.getOrNull()
                        val appLabel = appInfo?.let { pm.getApplicationLabel(it).toString() }
                            ?: pkgName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                        val appIcon = runCatching { pm.getApplicationIcon(pkgName) }.getOrNull()

                        val items = providerList.map { info ->
                            val label = runCatching { info.loadLabel(pm) }.getOrNull()
                                ?.takeIf { it.isNotBlank() }
                                ?: info.provider.shortClassName.substringAfterLast('.')
                            val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                runCatching { info.loadDescription(appContext)?.toString() }.getOrNull()
                            } else null

                            val preview = runCatching { info.loadPreviewImage(appContext, densityDpi) }.getOrNull()
                            val icon = runCatching { info.loadIcon(appContext, densityDpi) }.getOrNull() ?: appIcon

                            val (defaultW, defaultH) = calculateDefaultWidgetSizeDp(appContext, info)
                            val (cellsW, cellsH) = calculateWidgetCells(appContext, info, defaultW, defaultH)

                            val hasConfig = info.configure != null
                            val isConfigOptional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                                (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0

                            AvailableWidgetItem(
                                providerInfo = info,
                                label = label,
                                description = description,
                                previewImage = preview,
                                icon = icon,
                                defaultWidthDp = defaultW,
                                defaultHeightDp = defaultH,
                                cellsWidth = cellsW,
                                cellsHeight = cellsH,
                                hasConfiguration = hasConfig,
                                isConfigurationOptional = isConfigOptional
                            )
                        }.sortedBy { it.label.lowercase() }

                        if (items.isEmpty()) null else AvailableWidgetGroup(
                            packageName = pkgName,
                            appName = appLabel,
                            appIcon = appIcon,
                            widgets = items
                        )
                    }.sortedBy { it.appName.lowercase() }
                }

                _uiState.value = _uiState.value.copy(
                    availableWidgetGroups = groups,
                    isLoadingCatalog = false
                )
            } catch (e: Exception) {
                Log.e("WidgetViewModel", "Failed to load installed widget providers", e)
                _uiState.value = _uiState.value.copy(isLoadingCatalog = false)
            }
        }
    }

    fun addWidget(widgetId: Int, widgetInfo: AppWidgetProviderInfo) {
        viewModelScope.launch {
            try {
                // Avoid duplicate entry if already added
                if (_uiState.value.widgets.any { it.widgetId == widgetId }) {
                    notifyWidgetConfigured(widgetId)
                    return@launch
                }

                val appContext = getApplication<Application>()
                val (defaultWidthDp, defaultHeightDp) = calculateDefaultWidgetSizeDp(appContext, widgetInfo)
                val currentOrder = _uiState.value.widgets.size

                val config = AppWidgetConfig(
                    id = "${widgetInfo.provider.packageName}_${widgetInfo.provider.className}_$widgetId",
                    widgetId = widgetId,
                    height = defaultHeightDp,
                    width = defaultWidthDp,
                    borderless = false,
                    background = true,
                    themeColors = false,
                    order = currentOrder
                )
                withContext(Dispatchers.IO) {
                    repository.insertConfig(config)
                }

                val persistedWidget = PersistedWidget(widgetId, widgetInfo, config)
                _uiState.value = _uiState.value.copy(
                    widgets = _uiState.value.widgets + persistedWidget,
                    refreshTrigger = _uiState.value.refreshTrigger + 1
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to add widget: ${e.message}")
            }
        }
    }

    fun notifyWidgetConfigured(widgetId: Int) {
        val updatedInfo = appWidgetManager.getAppWidgetInfo(widgetId)
        _uiState.value = _uiState.value.copy(
            widgets = _uiState.value.widgets.map { w ->
                if (w.widgetId == widgetId && updatedInfo != null) {
                    w.copy(widgetInfo = updatedInfo)
                } else {
                    w
                }
            },
            refreshTrigger = _uiState.value.refreshTrigger + 1
        )
    }

    fun removeWidget(widgetId: Int) {
        viewModelScope.launch {
            try {
                val widget = _uiState.value.widgets.find { it.widgetId == widgetId }
                if (widget != null) {
                    withContext(Dispatchers.IO) {
                        repository.deleteConfig(widget.config)
                    }
                    _uiState.value = _uiState.value.copy(
                        widgets = _uiState.value.widgets.filter { it.widgetId != widgetId }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to remove widget: ${e.message}"
                )
            }
        }
    }

    fun updateWidgetConfig(widgetId: Int, updatedConfig: AppWidgetConfig) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.insertConfig(updatedConfig)
                }

                _uiState.value = _uiState.value.copy(
                    widgets = _uiState.value.widgets.map { widget ->
                        if (widget.widgetId == widgetId) {
                            widget.copy(config = updatedConfig)
                        } else {
                            widget
                        }
                    },
                    refreshTrigger = _uiState.value.refreshTrigger + 1
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update widget config: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearAllWidgets() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(widgets = emptyList())
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to clear widgets: ${e.message}"
                )
            }
        }
    }

    fun reorderWidgets(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            try {
                val currentWidgets = _uiState.value.widgets.toMutableList()
                if (fromIndex in currentWidgets.indices && toIndex in currentWidgets.indices) {
                    val widget = currentWidgets.removeAt(fromIndex)
                    currentWidgets.add(toIndex, widget)

                    val reindexed = currentWidgets.mapIndexed { index, persistedWidget ->
                        persistedWidget.copy(config = persistedWidget.config.copy(order = index))
                    }

                    _uiState.value = _uiState.value.copy(widgets = reindexed)

                    withContext(Dispatchers.IO) {
                        reindexed.forEach { persistedWidget ->
                            repository.insertConfig(persistedWidget.config)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to reorder widgets: ${e.message}"
                )
            }
        }
    }
}

fun calculateDefaultWidgetSizeDp(
    context: Context,
    widgetInfo: AppWidgetProviderInfo
): Pair<Int, Int> {
    val displayMetrics = context.resources.displayMetrics
    val density = displayMetrics.density.coerceAtLeast(1f)
    val screenWidthDp = (displayMetrics.widthPixels / density).roundToInt()
    val maxAvailableWidthDp = (screenWidthDp - 32).coerceAtLeast(240)

    // Note: AppWidgetProviderInfo.minWidth and minHeight are in PIXELS
    val minWidthDpFromPx = (widgetInfo.minWidth / density).roundToInt()
    val minHeightDpFromPx = (widgetInfo.minHeight / density).roundToInt()

    val targetCellsW = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        widgetInfo.targetCellWidth
    } else 0
    val targetCellsH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        widgetInfo.targetCellHeight
    } else 0

    val cellWidthDp = if (targetCellsW > 0) targetCellsW * 80 else 0
    val cellHeightDp = if (targetCellsH > 0) targetCellsH * 90 else 0

    val rawWidthDp = maxOf(minWidthDpFromPx, cellWidthDp)
    val rawHeightDp = maxOf(minHeightDpFromPx, cellHeightDp)

    val defaultWidthDp = when {
        rawWidthDp <= 0 -> maxAvailableWidthDp
        rawWidthDp >= 200 || targetCellsW >= 3 -> maxAvailableWidthDp
        else -> rawWidthDp.coerceIn(160, maxAvailableWidthDp)
    }

    val defaultHeightDp = when {
        rawHeightDp <= 0 -> 160
        else -> (rawHeightDp + 24).coerceIn(110, 380)
    }

    return defaultWidthDp to defaultHeightDp
}

private fun calculateWidgetCells(
    context: Context,
    widgetInfo: AppWidgetProviderInfo,
    defaultWidthDp: Int,
    defaultHeightDp: Int
): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val cw = widgetInfo.targetCellWidth
        val ch = widgetInfo.targetCellHeight
        if (cw > 0 && ch > 0) {
            return cw.coerceIn(1, 5) to ch.coerceIn(1, 5)
        }
    }
    val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
    val wDp = if (widgetInfo.minWidth > 0) (widgetInfo.minWidth / density) else defaultWidthDp.toFloat()
    val hDp = if (widgetInfo.minHeight > 0) (widgetInfo.minHeight / density) else defaultHeightDp.toFloat()
    val cellsW = ceil((wDp + 30f) / 70f).toInt().coerceIn(1, 5)
    val cellsH = ceil((hDp + 30f) / 70f).toInt().coerceIn(1, 5)
    return cellsW to cellsH
}