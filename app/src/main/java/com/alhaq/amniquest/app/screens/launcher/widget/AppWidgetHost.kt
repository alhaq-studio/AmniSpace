package com.alhaq.amniquest.app.screens.launcher.widget

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.util.SparseIntArray
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterViewFlipper
import android.widget.GridView
import android.widget.ListView
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.StackView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.iterator
import androidx.core.view.setPadding
import com.alhaq.amniquest.core.core.utils.material_color_utils.palettes.TonalPalette
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt

const val APPWIDGET_HOST_ID = 44203
const val REQUEST_CODE_CONFIGURE_WIDGET = 44204

data class WidgetConfigureResult(
    val appWidgetId: Int,
    val resultCode: Int
)

object WidgetConfigureResultManager {
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private val _results = MutableSharedFlow<WidgetConfigureResult>(extraBufferCapacity = 4)
    val results: SharedFlow<WidgetConfigureResult> = _results.asSharedFlow()

    fun setPendingWidgetId(appWidgetId: Int) {
        pendingWidgetId = appWidgetId
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        if (requestCode == REQUEST_CODE_CONFIGURE_WIDGET) {
            val idFromIntent = data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

            val resolvedId = if (idFromIntent != AppWidgetManager.INVALID_APPWIDGET_ID) {
                idFromIntent
            } else {
                pendingWidgetId
            }

            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            if (resolvedId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                _results.tryEmit(WidgetConfigureResult(resolvedId, resultCode))
            }
        }
    }
}

class LauncherAppWidgetHost(
    context: Context,
    hostId: Int = APPWIDGET_HOST_ID
) : android.appwidget.AppWidgetHost(context, hostId) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return LauncherAppWidgetHostView(context)
    }

    fun startConfigureActivity(activity: Activity, appWidgetId: Int): Boolean {
        return try {
            WidgetConfigureResultManager.setPendingWidgetId(appWidgetId)
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
            } else {
                null
            }
            startAppWidgetConfigureActivityForResult(
                activity,
                appWidgetId,
                0,
                REQUEST_CODE_CONFIGURE_WIDGET,
                options
            )
            true
        } catch (e: Exception) {
            Log.e("LauncherAppWidgetHost", "Failed to start configure activity for widget $appWidgetId", e)
            WidgetConfigureResultManager.setPendingWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)
            false
        }
    }
}

class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var lastWidthDp: Int = -1
    var lastHeightDp: Int = -1

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        try {
            super.updateAppWidget(remoteViews)
            enableNestedScroll(this)
        } catch (e: Exception) {
            Log.e("LauncherWidgetHostView", "Error applying RemoteViews", e)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (hasScrollableChild(this)) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun getErrorView(): View {
        return TextView(context).apply {
            text = "Unable to load widget. Tap Edit Widgets to resize or reconfigure."
            gravity = Gravity.CENTER
            textSize = 12f
            setPadding(24, 24, 24, 24)
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AppWidgetHost(
    widgetInfo: AppWidgetProviderInfo,
    widgetId: Int,
    modifier: Modifier = Modifier,
    borderless: Boolean = false,
    useThemeColors: Boolean = false,
    onLightBackground: Boolean = false,
    refreshKey: Int = 0
) {
    val padding = if (borderless) 0 else 4.dp.toPixels().roundToInt()

    val colorScheme = MaterialTheme.colorScheme
    val appWidgetHost = LocalAppWidgetHost.current
    val activityContext = LocalContext.current

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val widthDp = maxWidth.value.roundToInt().coerceAtLeast(60)
        val heightDp = maxHeight.value.roundToInt().coerceAtLeast(60)

        key(widgetId, refreshKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val view = appWidgetHost.createView(activityContext, widgetId, widgetInfo)
                    view.setAppWidget(widgetId, widgetInfo)
                    enableNestedScroll(view)
                    applyWidgetDimensions(view, activityContext, widgetId, widthDp, heightDp)
                    view
                },
                update = { view ->
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            view.setOnLightBackground(onLightBackground)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (useThemeColors) {
                                val colorMapping = getColorMapping(colorScheme)
                                view.setColorResources(colorMapping)
                            } else {
                                view.resetColorResources()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("AppWidgetHost", "Failed to apply color resources: ${e.message}")
                    }

                    view.setPadding(padding)

                    val hostView = view as? LauncherAppWidgetHostView
                    if (hostView == null || hostView.lastWidthDp != widthDp || hostView.lastHeightDp != heightDp) {
                        hostView?.lastWidthDp = widthDp
                        hostView?.lastHeightDp = heightDp
                        applyWidgetDimensions(view, activityContext, widgetId, widthDp, heightDp)
                    }
                }
            )
        }
    }
}

private fun applyWidgetDimensions(
    view: AppWidgetHostView,
    context: Context,
    widgetId: Int,
    widthDp: Int,
    heightDp: Int
) {
    try {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val existingOptions = appWidgetManager.getAppWidgetOptions(widgetId) ?: Bundle()
        val options = Bundle(existingOptions).apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            putInt(
                AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = ArrayList<SizeF>(2).apply {
                add(SizeF(widthDp.toFloat(), heightDp.toFloat()))
            }
            options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, sizes)
            view.updateAppWidgetSize(options, sizes)
        } else {
            @Suppress("DEPRECATION")
            view.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
        }
        appWidgetManager.updateAppWidgetOptions(widgetId, options)
    } catch (e: Exception) {
        Log.e("AppWidgetHost", "Failed to update widget size for $widgetId", e)
    }
}

private fun enableNestedScroll(view: View) {
    if (view is ViewGroup) {
        for (child in view.iterator()) {
            enableNestedScroll(child)
        }
    }
    if (view is ListView || view is ScrollView || view is android.widget.HorizontalScrollView || view is GridView) {
        view.isNestedScrollingEnabled = true
    }
}

private fun hasScrollableChild(view: View): Boolean {
    if (view is ListView || view is ScrollView || view is android.widget.HorizontalScrollView ||
        view is GridView || view is StackView || view is AdapterViewFlipper
    ) {
        return true
    }
    if (view is ViewGroup) {
        for (child in view.iterator()) {
            if (hasScrollableChild(child)) return true
        }
    }
    return false
}

@RequiresApi(Build.VERSION_CODES.S)
private fun getColorMapping(colorScheme: ColorScheme): SparseIntArray {
    val p = TonalPalette.fromInt(colorScheme.primary.toArgb())
    val s = TonalPalette.fromInt(colorScheme.secondary.toArgb())
    val t = TonalPalette.fromInt(colorScheme.tertiary.toArgb())
    val n = TonalPalette.fromInt(colorScheme.outline.toArgb())
    val nv = TonalPalette.fromInt(colorScheme.outlineVariant.toArgb())

    val colorResources = SparseIntArray()
    colorResources.append(android.R.color.system_accent1_0, p.tone(100))
    colorResources.append(android.R.color.system_accent1_10, p.tone(99))
    colorResources.append(android.R.color.system_accent1_100, p.tone(90))
    colorResources.append(android.R.color.system_accent1_200, p.tone(80))
    colorResources.append(android.R.color.system_accent1_300, p.tone(70))
    colorResources.append(android.R.color.system_accent1_400, p.tone(60))
    colorResources.append(android.R.color.system_accent1_500, p.tone(50))
    colorResources.append(android.R.color.system_accent1_600, p.tone(40))
    colorResources.append(android.R.color.system_accent1_700, p.tone(30))
    colorResources.append(android.R.color.system_accent1_800, p.tone(20))
    colorResources.append(android.R.color.system_accent1_900, p.tone(10))
    colorResources.append(android.R.color.system_accent1_1000, s.tone(0))
    colorResources.append(android.R.color.system_accent2_0, s.tone(100))
    colorResources.append(android.R.color.system_accent2_10, s.tone(99))
    colorResources.append(android.R.color.system_accent2_100, s.tone(90))
    colorResources.append(android.R.color.system_accent2_200, s.tone(80))
    colorResources.append(android.R.color.system_accent2_300, s.tone(70))
    colorResources.append(android.R.color.system_accent2_400, s.tone(60))
    colorResources.append(android.R.color.system_accent2_500, s.tone(50))
    colorResources.append(android.R.color.system_accent2_600, s.tone(40))
    colorResources.append(android.R.color.system_accent2_700, s.tone(30))
    colorResources.append(android.R.color.system_accent2_800, s.tone(20))
    colorResources.append(android.R.color.system_accent2_900, s.tone(10))
    colorResources.append(android.R.color.system_accent2_1000, t.tone(0))
    colorResources.append(android.R.color.system_accent3_0, t.tone(100))
    colorResources.append(android.R.color.system_accent3_10, t.tone(99))
    colorResources.append(android.R.color.system_accent3_100, t.tone(90))
    colorResources.append(android.R.color.system_accent3_200, t.tone(80))
    colorResources.append(android.R.color.system_accent3_300, t.tone(70))
    colorResources.append(android.R.color.system_accent3_400, t.tone(60))
    colorResources.append(android.R.color.system_accent3_500, t.tone(50))
    colorResources.append(android.R.color.system_accent3_600, t.tone(40))
    colorResources.append(android.R.color.system_accent3_700, t.tone(30))
    colorResources.append(android.R.color.system_accent3_800, t.tone(20))
    colorResources.append(android.R.color.system_accent3_900, t.tone(10))
    colorResources.append(android.R.color.system_accent3_1000, t.tone(0))
    colorResources.append(android.R.color.system_neutral1_0, n.tone(100))
    colorResources.append(android.R.color.system_neutral1_10, n.tone(99))
    colorResources.append(android.R.color.system_neutral1_100, n.tone(90))
    colorResources.append(android.R.color.system_neutral1_200, n.tone(80))
    colorResources.append(android.R.color.system_neutral1_300, n.tone(70))
    colorResources.append(android.R.color.system_neutral1_400, n.tone(60))
    colorResources.append(android.R.color.system_neutral1_500, n.tone(50))
    colorResources.append(android.R.color.system_neutral1_600, n.tone(40))
    colorResources.append(android.R.color.system_neutral1_700, n.tone(30))
    colorResources.append(android.R.color.system_neutral1_800, n.tone(20))
    colorResources.append(android.R.color.system_neutral1_900, n.tone(10))
    colorResources.append(android.R.color.system_neutral1_1000, nv.tone(0))
    colorResources.append(android.R.color.system_neutral2_0, nv.tone(100))
    colorResources.append(android.R.color.system_neutral2_10, nv.tone(99))
    colorResources.append(android.R.color.system_neutral2_100, nv.tone(90))
    colorResources.append(android.R.color.system_neutral2_200, nv.tone(80))
    colorResources.append(android.R.color.system_neutral2_300, nv.tone(70))
    colorResources.append(android.R.color.system_neutral2_400, nv.tone(60))
    colorResources.append(android.R.color.system_neutral2_500, nv.tone(50))
    colorResources.append(android.R.color.system_neutral2_600, nv.tone(40))
    colorResources.append(android.R.color.system_neutral2_700, nv.tone(30))
    colorResources.append(android.R.color.system_neutral2_800, nv.tone(20))
    colorResources.append(android.R.color.system_neutral2_900, nv.tone(10))
    colorResources.append(android.R.color.system_neutral2_1000, nv.tone(0))

    return colorResources
}

@Composable
fun Dp.toPixels(): Float {
    return value * LocalDensity.current.density
}