package com.alhaq.amniquest.app.screens.launcher.widget.picker

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
import com.alhaq.amniquest.backed.repositories.AppWidgetConfig
import com.alhaq.amniquest.backed.repositories.AppWidgetConfigDao
import com.alhaq.amniquest.backed.repositories.WidgetModule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    constructor(application: Application) : this(
        application,
        WidgetModule.provideAppWidgetConfigDao(WidgetModule.provideDatabase(application))
    )

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
                                    val key = "${info.provider?.flattenToString().orEmpty()}_${info.profile?.hashCode() ?: 0}"
                                    allProviders[key] = info
                                }
                            }
                        }
                    }

                    // Standard getInstalledProviders
                    runCatching {
                        appWidgetManager.installedProviders.forEach { info ->
                            val key = "${info.provider?.flattenToString().orEmpty()}_${info.profile?.hashCode() ?: 0}"
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
                                            val key = "${info.provider?.flattenToString().orEmpty()}_${info.profile?.hashCode() ?: 0}"
                                            allProviders[key] = info
                                        }
                                    }
                                }
                            } else {
                                runCatching {
                                    appWidgetManager.getInstalledProvidersForPackage(pkg, null).forEach { info ->
                                        val key = "${info.provider?.flattenToString().orEmpty()}_${info.profile?.hashCode() ?: 0}"
                                        allProviders[key] = info
                                    }
                                }
                            }
                        }
                    }

                    val byPackage = allProviders.values.groupBy { it.provider?.packageName.orEmpty() }.filterKeys { it.isNotEmpty() }

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
                if (_uiState.value.widgets.any { it.widgetId == widgetId }) {
                    notifyWidgetConfigured(widgetId)
                    return@launch
                }

                val appContext = getApplication<Application>()
                val (defaultWidthDp, defaultHeightDp) = calculateDefaultWidgetSizeDp(appContext, widgetInfo)
                val currentOrder = _uiState.value.widgets.size

                val config = AppWidgetConfig(
                    id = "${widgetInfo.provider?.packageName.orEmpty()}_${widgetInfo.provider?.className.orEmpty()}_$widgetId",
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
                } else {
                    withContext(Dispatchers.IO) {
                        repository.getConfigById(widgetId)?.let { repository.deleteConfig(it) }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    widgets = _uiState.value.widgets.filter { it.widgetId != widgetId }
                )
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
                withContext(Dispatchers.IO) {
                    repository.deleteAllConfigs()
                }
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

fun normalizePixelToDp(pixelValue: Int, density: Float): Int {
    val safeDensity = density.coerceAtLeast(1f)
    return (pixelValue / safeDensity).roundToInt()
}

fun calculateDefaultWidgetSizeDp(
    screenWidthPixels: Int,
    density: Float,
    minWidthPx: Int,
    minHeightPx: Int,
    targetCellWidth: Int = 0,
    targetCellHeight: Int = 0
): Pair<Int, Int> {
    val safeDensity = density.coerceAtLeast(1f)
    val screenWidthDp = (screenWidthPixels / safeDensity).roundToInt()
    val maxAvailableWidthDp = (screenWidthDp - 32).coerceAtLeast(240)

    val minWidthDpFromPx = normalizePixelToDp(minWidthPx, safeDensity)
    val minHeightDpFromPx = normalizePixelToDp(minHeightPx, safeDensity)

    val cellWidthDp = if (targetCellWidth > 0) targetCellWidth * 80 else 0
    val cellHeightDp = if (targetCellHeight > 0) targetCellHeight * 90 else 0

    val rawWidthDp = maxOf(minWidthDpFromPx, cellWidthDp)
    val rawHeightDp = maxOf(minHeightDpFromPx, cellHeightDp)

    val defaultWidthDp = when {
        rawWidthDp <= 0 -> maxAvailableWidthDp
        rawWidthDp >= 200 || targetCellWidth >= 3 -> maxAvailableWidthDp
        else -> rawWidthDp.coerceIn(160, maxAvailableWidthDp)
    }

    val defaultHeightDp = when {
        rawHeightDp <= 0 -> 160
        else -> (rawHeightDp + 24).coerceIn(110, 380)
    }

    return defaultWidthDp to defaultHeightDp
}

fun calculateDefaultWidgetSizeDp(
    context: Context,
    widgetInfo: AppWidgetProviderInfo
): Pair<Int, Int> {
    val displayMetrics = context.resources.displayMetrics
    val targetCellsW = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        widgetInfo.targetCellWidth
    } else 0
    val targetCellsH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        widgetInfo.targetCellHeight
    } else 0

    return calculateDefaultWidgetSizeDp(
        screenWidthPixels = displayMetrics.widthPixels,
        density = displayMetrics.density,
        minWidthPx = widgetInfo.minWidth,
        minHeightPx = widgetInfo.minHeight,
        targetCellWidth = targetCellsW,
        targetCellHeight = targetCellsH
    )
}

fun calculateWidgetCells(
    density: Float,
    minWidthPx: Int,
    minHeightPx: Int,
    targetCellWidth: Int = 0,
    targetCellHeight: Int = 0,
    defaultWidthDp: Int = 200,
    defaultHeightDp: Int = 160
): Pair<Int, Int> {
    if (targetCellWidth > 0 && targetCellHeight > 0) {
        return targetCellWidth.coerceIn(1, 5) to targetCellHeight.coerceIn(1, 5)
    }
    val safeDensity = density.coerceAtLeast(1f)
    val wDp = if (minWidthPx > 0) (minWidthPx / safeDensity) else defaultWidthDp.toFloat()
    val hDp = if (minHeightPx > 0) (minHeightPx / safeDensity) else defaultHeightDp.toFloat()
    val cellsW = ceil((wDp + 30f) / 70f).toInt().coerceIn(1, 5)
    val cellsH = ceil((hDp + 30f) / 70f).toInt().coerceIn(1, 5)
    return cellsW to cellsH
}

fun calculateWidgetCells(
    context: Context,
    widgetInfo: AppWidgetProviderInfo,
    defaultWidthDp: Int,
    defaultHeightDp: Int
): Pair<Int, Int> {
    val targetCellsW = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        widgetInfo.targetCellWidth
    } else 0
    val targetCellsH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        widgetInfo.targetCellHeight
    } else 0

    return calculateWidgetCells(
        density = context.resources.displayMetrics.density,
        minWidthPx = widgetInfo.minWidth,
        minHeightPx = widgetInfo.minHeight,
        targetCellWidth = targetCellsW,
        targetCellHeight = targetCellsH,
        defaultWidthDp = defaultWidthDp,
        defaultHeightDp = defaultHeightDp
    )
}

fun filterAvailableWidgetGroups(
    groups: List<AvailableWidgetGroup>,
    query: String
): List<AvailableWidgetGroup> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return groups
    return groups.mapNotNull { group ->
        val appMatches = group.appName.lowercase().contains(q) ||
            group.packageName.lowercase().contains(q)
        val matchingWidgets = if (appMatches) {
            group.widgets
        } else {
            group.widgets.filter { w ->
                w.label.lowercase().contains(q) ||
                    (w.description?.lowercase()?.contains(q) == true)
            }
        }
        if (matchingWidgets.isEmpty()) null else group.copy(widgets = matchingWidgets)
    }
}