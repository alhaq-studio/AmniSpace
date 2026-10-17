package neth.iecal.questphone.app.screens.launcher.widget

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import neth.iecal.questphone.app.navigation.RootRoute
import neth.iecal.questphone.app.screens.launcher.widget.picker.PersistedWidget
import neth.iecal.questphone.app.screens.launcher.widget.picker.WidgetViewModel

@Composable
fun WidgetScreen(
    navController: NavController,
    viewModel: WidgetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var isWidgetManagement by remember { mutableStateOf(false) }
    var autoOpenPicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        WidgetConfigureResultManager.results.collect { result ->
            viewModel.notifyWidgetConfigured(result.appWidgetId)
        }
    }

    BackHandler(isWidgetManagement) {
        isWidgetManagement = false
        autoOpenPicker = false
    }

    ProvideAppWidgetHost {
        if (!isWidgetManagement) {
            val listState = rememberLazyListState()
            val density = LocalDensity.current
            val pullThresholdPx = with(density) { 120.dp.toPx() }
            var overscrollPullPx by remember { mutableFloatStateOf(0f) }
            var navigatedHome by remember { mutableStateOf(false) }

            val nestedScrollConnection = remember(pullThresholdPx) {
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        if (source == NestedScrollSource.UserInput &&
                            available.y > 0f &&
                            listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset == 0
                        ) {
                            overscrollPullPx += available.y
                            if (overscrollPullPx >= pullThresholdPx && !navigatedHome) {
                                navigatedHome = true
                                navController.navigate(RootRoute.HomeScreen.route) {
                                    restoreState = true
                                }
                            }
                        } else if (available.y < 0f || consumed.y < 0f) {
                            overscrollPullPx = 0f
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPostFling(
                        consumed: Velocity,
                        available: Velocity
                    ): Velocity {
                        overscrollPullPx = 0f
                        return Velocity.Zero
                    }
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Text(
                                            text = "No widgets added yet",
                                            style = MaterialTheme.typography.titleMedium,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Add Android and app widgets to view them right from your launcher.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(20.dp))
                                        Button(
                                            onClick = {
                                                autoOpenPicker = true
                                                isWidgetManagement = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Add Your First Widget")
                                        }
                                    }
                                }
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .consumeWindowInsets(padding),
                                    state = listState,
                                    contentPadding = PaddingValues(vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    items(
                                        items = uiState.widgets,
                                        key = { it.widgetId }
                                    ) { persistedWidget ->
                                        WidgetCard(
                                            persistedWidget = persistedWidget,
                                            refreshTrigger = uiState.refreshTrigger
                                        )
                                    }
                                    item {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                                        ) {
                                            FilledTonalButton(
                                                onClick = {
                                                    autoOpenPicker = true
                                                    isWidgetManagement = true
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Add Widget"
                                                )
                                                Spacer(Modifier.size(8.dp))
                                                Text("Add Widget")
                                            }
                                            Button(
                                                onClick = {
                                                    autoOpenPicker = false
                                                    isWidgetManagement = true
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Widgets"
                                                )
                                                Spacer(Modifier.size(8.dp))
                                                Text("Edit Widgets")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            WidgetManagementScreen(
                viewModel = viewModel,
                initialOpenPicker = autoOpenPicker,
                onConsumeInitialOpenPicker = { autoOpenPicker = false },
                onBack = {
                    isWidgetManagement = false
                    autoOpenPicker = false
                }
            )
        }
    }
}

@Composable
private fun WidgetCard(
    persistedWidget: PersistedWidget,
    refreshTrigger: Int = 0
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val availableWidth = maxWidth
        val targetWidth = (persistedWidget.config.width?.dp ?: availableWidth)
            .coerceIn(120.dp, availableWidth.coerceAtLeast(120.dp))
        val targetHeight = persistedWidget.config.height.dp
            .coerceIn(80.dp, 520.dp)

        Box(
            modifier = Modifier
                .width(targetWidth)
                .height(targetHeight)
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
}

val LocalAppWidgetHost =
    staticCompositionLocalOf<LauncherAppWidgetHost> { error("AppWidgetHost not provided") }

@Composable
fun ProvideAppWidgetHost(content: @Composable () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val widgetHost = remember {
        LauncherAppWidgetHost(context.applicationContext, APPWIDGET_HOST_ID).also { host ->
            try {
                host.startListening()
            } catch (e: Exception) {
                Log.e("WidgetHost", "Error starting listener initially", e)
            }
        }
    }

    DisposableEffect(lifecycleOwner, widgetHost) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    try {
                        widgetHost.startListening()
                    } catch (e: Exception) {
                        Log.e("WidgetHost", "Error starting listener on ON_START", e)
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    try {
                        widgetHost.stopListening()
                    } catch (e: Exception) {
                        Log.e("WidgetHost", "Error stopping listener on ON_STOP", e)
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                widgetHost.stopListening()
            } catch (e: Exception) {
                Log.e("WidgetHost", "Error stopping listener on dispose", e)
            }
        }
    }

    CompositionLocalProvider(LocalAppWidgetHost provides widgetHost, content = content)
}