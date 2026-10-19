package com.alhaq.amniquest.app.screens.launcher.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.alhaq.amniquest.app.screens.launcher.widget.picker.AvailableWidgetGroup
import com.alhaq.amniquest.app.screens.launcher.widget.picker.AvailableWidgetItem
import com.alhaq.amniquest.app.screens.launcher.widget.picker.PersistedWidget
import com.alhaq.amniquest.app.screens.launcher.widget.picker.WidgetViewModel
import com.alhaq.amniquest.app.screens.launcher.widget.picker.calculateDefaultWidgetSizeDp
import com.alhaq.amniquest.app.screens.launcher.widget.picker.filterAvailableWidgetGroups
import com.alhaq.amniquest.backed.repositories.AppWidgetConfig
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetManagementScreen(
    viewModel: WidgetViewModel = hiltViewModel(),
    initialOpenPicker: Boolean = false,
    onConsumeInitialOpenPicker: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showWidgetPicker by remember { mutableStateOf(initialOpenPicker) }
    var pendingBindWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var pendingBindProvider by remember { mutableStateOf<AppWidgetProviderInfo?>(null) }

    LaunchedEffect(initialOpenPicker) {
        if (initialOpenPicker) {
            showWidgetPicker = true
            onConsumeInitialOpenPicker()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val appWidgetHost = LocalAppWidgetHost.current
    val appWidgetManager = remember(context) { AppWidgetManager.getInstance(context) }

    val configureWidgetFallbackLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val widgetId = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                pendingBindWidgetId
            )?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID } ?: pendingBindWidgetId

            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val info = appWidgetManager.getAppWidgetInfo(widgetId) ?: pendingBindProvider
                if (info != null) {
                    viewModel.addWidget(widgetId, info)
                    viewModel.notifyWidgetConfigured(widgetId)
                }
            }
            pendingBindWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            pendingBindProvider = null
        }

    fun launchWidgetConfiguration(appWidgetId: Int, info: AppWidgetProviderInfo) {
        if (info.configure == null) return
        pendingBindWidgetId = appWidgetId
        pendingBindProvider = info

        val startedViaHost = if (activity != null) {
            appWidgetHost.startConfigureActivity(activity, appWidgetId)
        } else {
            false
        }

        if (!startedViaHost) {
            try {
                val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = info.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                configureWidgetFallbackLauncher.launch(configIntent)
            } catch (e: Exception) {
                Log.e("WidgetManagement", "Fallback configure intent failed for $appWidgetId", e)
            }
        }
    }

    fun onWidgetBoundSuccessfully(appWidgetId: Int, info: AppWidgetProviderInfo) {
        viewModel.addWidget(appWidgetId, info)

        if (info.configure != null) {
            val isConfigOptional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
            if (!isConfigOptional) {
                launchWidgetConfiguration(appWidgetId, info)
            }
        }
    }

    val bindWidgetLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val returnedId = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                pendingBindWidgetId
            ) ?: pendingBindWidgetId

            val resolvedId = if (returnedId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                returnedId
            } else {
                pendingBindWidgetId
            }

            if (resolvedId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val boundInfo = appWidgetManager.getAppWidgetInfo(resolvedId)
                if (result.resultCode == Activity.RESULT_OK || boundInfo != null) {
                    val finalInfo = boundInfo ?: pendingBindProvider
                    if (finalInfo != null) {
                        onWidgetBoundSuccessfully(resolvedId, finalInfo)
                    }
                } else {
                    try {
                        appWidgetHost.deleteAppWidgetId(resolvedId)
                    } catch (e: Exception) {
                        Log.w("WidgetManagement", "Failed to delete canceled widget id $resolvedId", e)
                    }
                }
            }
            pendingBindWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            pendingBindProvider = null
        }

    fun requestBindAndAddWidget(item: AvailableWidgetItem) {
        showWidgetPicker = false
        val info = item.providerInfo
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val (defaultW, defaultH) = calculateDefaultWidgetSizeDp(context, info)

        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaultW)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defaultH)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, defaultW)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, defaultH)
            putInt(
                AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
            )
        }

        val boundImmediately = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId,
                info.profile,
                info.provider,
                options
            )
        } catch (e: Exception) {
            try {
                appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
            } catch (e2: Exception) {
                false
            }
        }

        if (boundImmediately) {
            onWidgetBoundSuccessfully(appWidgetId, info)
        } else {
            pendingBindWidgetId = appWidgetId
            pendingBindProvider = info
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
            }
            try {
                bindWidgetLauncher.launch(bindIntent)
            } catch (e: Exception) {
                Log.e("WidgetManagement", "Failed to launch ACTION_APPWIDGET_BIND", e)
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
        }
    }

    if (showWidgetPicker) {
        WidgetPickerBottomSheet(
            groups = uiState.availableWidgetGroups,
            isLoading = uiState.isLoadingCatalog,
            onRefresh = { viewModel.loadAvailableWidgets() },
            onDismiss = { showWidgetPicker = false },
            onSelectWidget = { item -> requestBindAndAddWidget(item) }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                title = {
                    Text(
                        text = "Widget Management",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.loadAvailableWidgets()
                            showWidgetPicker = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Widget")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.loadAvailableWidgets()
                    showWidgetPicker = true
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Widget"
                )
            }
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.widgets.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "No widgets added yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap the button below to browse all Android and app widgets installed on your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                viewModel.loadAvailableWidgets()
                                showWidgetPicker = true
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse and Add Widgets")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
                ) {
                    itemsIndexed(
                        items = uiState.widgets,
                        key = { _, widget -> widget.widgetId }
                    ) { index, persistedWidget ->
                        WidgetManagementCard(
                            persistedWidget = persistedWidget,
                            index = index,
                            totalCount = uiState.widgets.size,
                            refreshTrigger = uiState.refreshTrigger,
                            onMoveUp = {
                                if (index > 0) {
                                    viewModel.reorderWidgets(index, index - 1)
                                }
                            },
                            onMoveDown = {
                                if (index < uiState.widgets.size - 1) {
                                    viewModel.reorderWidgets(index, index + 1)
                                }
                            },
                            onConfigure = {
                                launchWidgetConfiguration(
                                    persistedWidget.widgetId,
                                    persistedWidget.widgetInfo
                                )
                            },
                            onDelete = {
                                try {
                                    appWidgetHost.deleteAppWidgetId(persistedWidget.widgetId)
                                } catch (e: Exception) {
                                    Log.w("WidgetManagement", "Failed to delete host id", e)
                                }
                                viewModel.removeWidget(persistedWidget.widgetId)
                            },
                            onConfigChange = { updatedConfig ->
                                viewModel.updateWidgetConfig(
                                    persistedWidget.widgetId,
                                    updatedConfig
                                )
                            },
                            modifier = Modifier.animateItem(
                                placementSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                                fadeInSpec = tween(180),
                                fadeOutSpec = tween(180)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetManagementCard(
    persistedWidget: PersistedWidget,
    index: Int,
    totalCount: Int,
    refreshTrigger: Int,
    modifier: Modifier,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onConfigure: () -> Unit,
    onDelete: () -> Unit,
    onConfigChange: (AppWidgetConfig) -> Unit
) {
    val context = LocalContext.current
    val pm = remember(context) { context.packageManager }
    val label = remember(persistedWidget.widgetInfo) {
        runCatching { persistedWidget.widgetInfo.loadLabel(pm) }.getOrNull()
            ?: persistedWidget.widgetInfo.label
            ?: "Widget"
    }
    val hasConfigureActivity = persistedWidget.widgetInfo.configure != null

    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val maxCardWidthDp = maxWidth.value.coerceAtLeast(180f)
            val initialWidthDp = (persistedWidget.config.width?.toFloat() ?: maxCardWidthDp)
                .coerceIn(120f, maxCardWidthDp)
            val initialHeightDp = persistedWidget.config.height.toFloat()
                .coerceIn(80f, 500f)

            val widthState = remember(persistedWidget.widgetId, maxCardWidthDp) {
                mutableStateOf(initialWidthDp.dp)
            }
            val heightState = remember(persistedWidget.widgetId) {
                mutableStateOf(initialHeightDp.dp)
            }

            // 150ms debounce throttle for dimensions on low-RAM devices
            var isInitialLoad by remember { mutableStateOf(true) }
            LaunchedEffect(widthState.value, heightState.value) {
                if (isInitialLoad) {
                    isInitialLoad = false
                    return@LaunchedEffect
                }
                delay(150L)
                val wInt = widthState.value.value.roundToInt()
                val hInt = heightState.value.value.roundToInt()
                if (wInt != persistedWidget.config.width || hInt != persistedWidget.config.height) {
                    onConfigChange(
                        persistedWidget.config.copy(
                            width = wInt,
                            height = hInt
                        )
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Position ${index + 1} of $totalCount | ${widthState.value.value.roundToInt()} x ${heightState.value.value.roundToInt()} dp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasConfigureActivity) {
                            IconButton(onClick = onConfigure) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Configure Widget"
                                )
                            }
                        }

                        IconButton(
                            onClick = onMoveUp,
                            enabled = index > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Move Up",
                            )
                        }

                        IconButton(
                            onClick = onMoveDown,
                            enabled = index < totalCount - 1
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Move Down",
                            )
                        }

                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Widget",
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live widget preview box
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    ResizableBox(
                        width = widthState,
                        height = heightState,
                        minWidth = 120.dp,
                        maxWidth = maxCardWidthDp.dp,
                        minHeight = 80.dp,
                        maxHeight = 500.dp,
                        resizingEnabled = isExpanded
                    ) {
                        AppWidgetHost(
                            widgetInfo = persistedWidget.widgetInfo,
                            widgetId = persistedWidget.widgetId,
                            borderless = persistedWidget.config.borderless,
                            useThemeColors = persistedWidget.config.themeColors,
                            refreshKey = refreshTrigger,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (isExpanded) {
                                onConfigChange(
                                    persistedWidget.config.copy(
                                        width = widthState.value.value.roundToInt(),
                                        height = heightState.value.value.roundToInt()
                                    )
                                )
                            }
                            isExpanded = !isExpanded
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (isExpanded) "Save Size and Options" else "Resize and Customize"
                        )
                    }

                    if (hasConfigureActivity) {
                        OutlinedButton(onClick = onConfigure) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Setup")
                        }
                    }
                }

                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider()

                        // Quick size presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    widthState.value = maxCardWidthDp.dp
                                    heightState.value = 130.dp
                                    onConfigChange(
                                        persistedWidget.config.copy(
                                            width = maxCardWidthDp.roundToInt(),
                                            height = 130
                                        )
                                    )
                                },
                                label = { Text("Compact") }
                            )
                            AssistChip(
                                onClick = {
                                    widthState.value = maxCardWidthDp.dp
                                    heightState.value = 200.dp
                                    onConfigChange(
                                        persistedWidget.config.copy(
                                            width = maxCardWidthDp.roundToInt(),
                                            height = 200
                                        )
                                    )
                                },
                                label = { Text("Medium") }
                            )
                            AssistChip(
                                onClick = {
                                    widthState.value = maxCardWidthDp.dp
                                    heightState.value = 300.dp
                                    onConfigChange(
                                        persistedWidget.config.copy(
                                            width = maxCardWidthDp.roundToInt(),
                                            height = 300
                                        )
                                    )
                                },
                                label = { Text("Large") }
                            )
                        }

                        // Width slider
                        Text(
                            text = "Width: ${widthState.value.value.roundToInt()} dp",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = widthState.value.value.coerceIn(120f, maxCardWidthDp),
                            onValueChange = { newW -> widthState.value = newW.dp },
                            onValueChangeFinished = {
                                onConfigChange(
                                    persistedWidget.config.copy(
                                        width = widthState.value.value.roundToInt(),
                                        height = heightState.value.value.roundToInt()
                                    )
                                )
                            },
                            valueRange = 120f..maxCardWidthDp.coerceAtLeast(140f)
                        )

                        // Height slider
                        Text(
                            text = "Height: ${heightState.value.value.roundToInt()} dp",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = heightState.value.value.coerceIn(80f, 500f),
                            onValueChange = { newH -> heightState.value = newH.dp },
                            onValueChangeFinished = {
                                onConfigChange(
                                    persistedWidget.config.copy(
                                        width = widthState.value.value.roundToInt(),
                                        height = heightState.value.value.roundToInt()
                                    )
                                )
                            },
                            valueRange = 80f..500f
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Apply Launcher Theme Colors",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = persistedWidget.config.themeColors,
                                    onCheckedChange = { checked ->
                                        onConfigChange(persistedWidget.config.copy(themeColors = checked))
                                    }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Borderless Padding",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = persistedWidget.config.borderless,
                                onCheckedChange = { checked ->
                                    onConfigChange(persistedWidget.config.copy(borderless = checked))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetPickerBottomSheet(
    groups: List<AvailableWidgetGroup>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onSelectWidget: (AvailableWidgetItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val expandedApps = remember { mutableStateMapOf<String, Boolean>() }

    val filteredGroups = remember(groups, searchQuery) {
        filterAvailableWidgetGroups(groups, searchQuery)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select a Widget",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap any widget to add and configure it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Widgets"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps or widgets...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                filteredGroups.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "No widgets found on this device."
                            } else {
                                "No widgets matching \"$searchQuery\"."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(
                            items = filteredGroups,
                            key = { it.packageName }
                        ) { group ->
                            val isExpanded = if (searchQuery.isNotBlank()) {
                                true
                            } else {
                                expandedApps[group.packageName] ?: false
                            }

                            AppWidgetGroupCard(
                                group = group,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    expandedApps[group.packageName] = !isExpanded
                                },
                                onSelectWidget = onSelectWidget
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppWidgetGroupCard(
    group: AvailableWidgetGroup,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectWidget: (AvailableWidgetItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    DrawableImage(
                        drawable = group.appIcon,
                        contentDescription = group.appName,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = group.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${group.widgets.size} widget${if (group.widgets.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    group.widgets.forEach { widgetItem ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectWidget(widgetItem) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = widgetItem.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${widgetItem.cellsWidth} x ${widgetItem.cellsHeight} cells (${widgetItem.defaultWidthDp} x ${widgetItem.defaultHeightDp} dp)" +
                                                if (widgetItem.hasConfiguration) " | Configurable" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!widgetItem.description.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = widgetItem.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = { onSelectWidget(widgetItem) },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add")
                                    }
                                }

                                if (widgetItem.previewImage != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 64.dp, max = 140.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        DrawableImage(
                                            drawable = widgetItem.previewImage,
                                            contentDescription = widgetItem.label,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.heightIn(max = 124.dp)
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
}

@Composable
private fun DrawableImage(
    drawable: Drawable?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val imageBitmap: ImageBitmap? = remember(drawable) {
        drawable?.toImageBitmapOrNull()
    }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier)
    }
}

private fun Drawable.toImageBitmapOrNull(): ImageBitmap? {
    return try {
        if (this is BitmapDrawable && this.bitmap != null) {
            return this.bitmap.asImageBitmap()
        }
        val w = intrinsicWidth.takeIf { it > 0 } ?: 128
        val h = intrinsicHeight.takeIf { it > 0 } ?: 128
        val bmp = Bitmap.createBitmap(w.coerceAtMost(800), h.coerceAtMost(800), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ResizableBox(
    modifier: Modifier = Modifier,
    width: MutableState<Dp>,
    height: MutableState<Dp>,
    minWidth: Dp = 80.dp,
    maxWidth: Dp = 400.dp,
    minHeight: Dp = 80.dp,
    maxHeight: Dp = 500.dp,
    resizingEnabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current

    fun Float.pxToDp(): Dp = with(density) { this@pxToDp.toDp() }

    Box(
        modifier = modifier
            .width(width.value.coerceIn(minWidth, maxWidth))
            .height(height.value.coerceIn(minHeight, maxHeight))
            .border(
                width = if (resizingEnabled) 1.5.dp else 0.dp,
                color = if (resizingEnabled) primaryColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        content()

        if (resizingEnabled) {
            val handleSize = 20.dp

            fun Modifier.resizeHandle(
                onDrag: (Offset) -> Unit
            ) = this
                .size(handleSize)
                .background(primaryColor, shape = CircleShape)
                .border(1.dp, surfaceColor, CircleShape)
                .pointerInput(minWidth, maxWidth, minHeight, maxHeight) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
                }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .resizeHandle { drag ->
                        width.value = (width.value - drag.x.pxToDp()).coerceIn(minWidth, maxWidth)
                        height.value = (height.value - drag.y.pxToDp()).coerceIn(minHeight, maxHeight)
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .resizeHandle { drag ->
                        width.value = (width.value + drag.x.pxToDp()).coerceIn(minWidth, maxWidth)
                        height.value = (height.value - drag.y.pxToDp()).coerceIn(minHeight, maxHeight)
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .resizeHandle { drag ->
                        width.value = (width.value - drag.x.pxToDp()).coerceIn(minWidth, maxWidth)
                        height.value = (height.value + drag.y.pxToDp()).coerceIn(minHeight, maxHeight)
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .resizeHandle { drag ->
                        width.value = (width.value + drag.x.pxToDp()).coerceIn(minWidth, maxWidth)
                        height.value = (height.value + drag.y.pxToDp()).coerceIn(minHeight, maxHeight)
                    }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .resizeHandle { drag ->
                        width.value = (width.value - drag.x.pxToDp()).coerceIn(minWidth, maxWidth)
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .resizeHandle { drag ->
                        width.value = (width.value + drag.x.pxToDp()).coerceIn(minWidth, maxWidth)
                    }
            )
        }
    }
}
