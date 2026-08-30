package com.alhaq.amniquest.app.screens.launcher

import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlin.math.abs
import com.alhaq.amniquest.app.screens.launcher.dialogs.HomeStudioDialog
import com.alhaq.amniquest.app.theme.backgrounds.HomeBackgroundRenderer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import com.alhaq.amniquest.BuildConfig
import com.alhaq.amniquest.R
import com.alhaq.amniquest.app.navigation.LauncherDialogRoutes
import com.alhaq.amniquest.app.navigation.RootRoute
import com.alhaq.amniquest.app.screens.components.TopBarActions
import com.alhaq.amniquest.app.screens.launcher.dialogs.DonationsDialog
import com.alhaq.amniquest.app.screens.launcher.dialogs.LauncherDialog
import com.alhaq.amniquest.app.screens.quest.setup.deep_focus.SelectAppsDialog
import com.alhaq.amniquest.app.theme.LocalCustomTheme
import com.alhaq.amniquest.app.theme.smoothRed
import com.alhaq.amniquest.core.services.LockScreenService
import com.alhaq.amniquest.core.services.performLockScreenAction
import com.alhaq.amniquest.core.utils.managers.QuestHelper
import com.alhaq.amniquest.core.core.utils.managers.isAccessibilityServiceEnabled
import com.alhaq.amniquest.core.core.utils.managers.isSetToDefaultLauncher
import com.alhaq.amniquest.core.core.utils.managers.openAccessibilityServiceScreen
import com.alhaq.amniquest.core.core.utils.managers.openDefaultLauncherSettings
import java.lang.reflect.Method

data class SidePanelItem(
    val icon: Int,
    val onClick: () -> Unit,
    val contentDesc: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun HomeScreen(
    navController: NavController?,
    viewModel: HomeScreenViewModel,
) {
    val context = LocalContext.current

    val time by viewModel.time
    val questList by viewModel.questList.collectAsState()

    val completedQuests by viewModel.completedQuests.collectAsState()
    val shortcuts = viewModel.shortcuts
    val tempShortcuts = viewModel.tempShortcuts
    val coins by viewModel.coins.collectAsState()
    val streak by viewModel.currentStreak.collectAsState()
    var isAppSelectorVisible by remember { mutableStateOf(false) }

    val privateSettingsSp = remember { context.getSharedPreferences("private_settings", android.content.Context.MODE_PRIVATE) }
    var isPrivacyModeEnabled by remember { mutableStateOf(privateSettingsSp.getBoolean("habit_privacy_mode", false)) }
    var isPrivateQuestsUnlocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isPrivacyModeEnabled = privateSettingsSp.getBoolean("habit_privacy_mode", false)
    }

    val triggerBiometricUnlock = remember(context) {
        { onSuccess: () -> Unit ->
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity != null) {
                val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                val biometricPrompt = androidx.biometric.BiometricPrompt(
                    activity,
                    executor,
                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                        }
                    }
                )

                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Private Quests")
                    .setSubtitle("Authenticate to view and interact with your habits")
                    .setAllowedAuthenticators(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()

                try {
                    biometricPrompt.authenticate(promptInfo)
                } catch (e: Exception) {
                    onSuccess()
                }
            } else {
                onSuccess()
            }
        }
    }


    val sidePanelItems = listOf<SidePanelItem>(
        SidePanelItem(
            R.drawable.ic_profile,
            { navController?.navigate(RootRoute.UserInfo.route) },
            "Profile"
        ),
        SidePanelItem(
            R.drawable.ic_customize,
            { navController?.navigate(RootRoute.Customize.route) },
            "Customize"
        ),
        SidePanelItem(
            R.drawable.ic_store,
            { navController?.navigate(RootRoute.Store.route) },
            "Store"
        ),
        SidePanelItem(
            com.alhaq.amniquest.data.R.drawable.ic_analytics,
            { navController?.navigate(RootRoute.ListAllQuest.route) },
            "Quest Analytics"
        ),
        SidePanelItem(
            com.alhaq.amniquest.data.R.drawable.ic_quest_add,
            { navController?.navigate(RootRoute.SelectTemplates.route) },
            "Add Quest"
        )
    )

    var isAllQuestsDialogVisible by remember { mutableStateOf(false) }
    var isHomeStudioVisible by remember { mutableStateOf(false) }

    val customizationInfo by viewModel.customizationInfo

    val infiniteTransition = rememberInfiniteTransition(label = "floating")
    val swipeIconAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f, // move up 10 dp (negative is up in Compose)
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )

    val showDonationDialog by viewModel.showDonationsDialog.collectAsState()

    val hapticFeedback = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var isDoubleTapToSleepEnabled by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isDoubleTapToSleepEnabled = com.alhaq.amniquest.BuildConfig.IS_FDROID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isAccessibilityServiceEnabled(
                    context,
                    LockScreenService::class.java
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    // duck tape fix, trying to figure out a different way to do it without problems
    LaunchedEffect(Unit) {
        viewModel.handleCheckStreakFailure()
        viewModel.filterQuests()
    }


    BackHandler(isAppSelectorVisible || isAllQuestsDialogVisible || isHomeStudioVisible) {
        if (isAppSelectorVisible) isAppSelectorVisible = false
        if (isAllQuestsDialogVisible) isAllQuestsDialogVisible = false
        if (isHomeStudioVisible) isHomeStudioVisible = false
    }

    if (showDonationDialog) {
        DonationsDialog {
            viewModel.hideDonationDialog()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = LocalCustomTheme.current.getRootColorScheme().surface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { innerPadding ->

        if (isAppSelectorVisible) {
            SelectAppsDialog(
                tempShortcuts,
                onDismiss = { isAppSelectorVisible = false },
                onConfirm = {
                    viewModel.saveShortcuts()
                    isAppSelectorVisible = false
                })
        }
        if (isAllQuestsDialogVisible) {
            LauncherDialog(
                onDismiss = { isAllQuestsDialogVisible = false },
                rootNavController = navController,
                startDestination = LauncherDialogRoutes.ShowAllQuest.route
            )
        }
        if (isHomeStudioVisible) {
            HomeStudioDialog(
                customizationInfo = customizationInfo,
                onDismiss = { isHomeStudioVisible = false },
                onSave = { updated ->
                    viewModel.updateCustomizationInfo(updated)
                },
                onOpenStore = {
                    navController?.navigate(RootRoute.Store.route)
                },
                onOpenCustomize = {
                    navController?.navigate(RootRoute.Customize.route)
                }
            )
        }


        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    var totalDragX = 0f
                    var totalDragY = 0f
                    var gestureHandled = false

                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                            totalDragY = 0f
                            gestureHandled = false
                        },
                        onDragEnd = {
                            totalDragX = 0f
                            totalDragY = 0f
                            gestureHandled = false
                        },
                        onDragCancel = {
                            totalDragX = 0f
                            totalDragY = 0f
                            gestureHandled = false
                        },
                        onDrag = { change, dragAmount ->
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            if (!gestureHandled) {
                                val absX = abs(totalDragX)
                                val absY = abs(totalDragY)

                                if (absY > absX && absY > 28f) {
                                    if (totalDragY < -28f) {
                                        // Swipe UP -> App List
                                        gestureHandled = true
                                        change.consume()
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        navController?.navigate(RootRoute.AppList.route) {
                                            launchSingleTop = true
                                        }
                                    } else if (totalDragY > 40f) {
                                        // Swipe DOWN -> Notification Panel
                                        gestureHandled = true
                                        change.consume()
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        try {
                                            context.getSystemService("statusbar")?.let { service ->
                                                val statusbarManager = Class.forName("android.app.StatusBarManager")
                                                val expand: Method = statusbarManager.getMethod("expandNotificationsPanel")
                                                expand.invoke(service)
                                            }
                                        } catch (e: Exception) {
                                            Log.e(
                                                "HomeScreen",
                                                "Error opening notification panel.",
                                                e
                                            )
                                        }
                                    }
                                } else if (absX > absY && absX > 28f) {
                                    if (totalDragX < -28f) {
                                        // Swipe LEFT -> Widget Screen
                                        gestureHandled = true
                                        change.consume()
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        navController?.navigate(RootRoute.WidgetScreen.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(isDoubleTapToSleepEnabled) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isDoubleTapToSleepEnabled) {
                                performLockScreenAction()
                            } else if (com.alhaq.amniquest.BuildConfig.IS_FDROID) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = "Enable Accessibility Service to use double-tap to sleep.",
                                        actionLabel = "Open",
                                        duration = SnackbarDuration.Short
                                    ).also { result ->
                                        if (result == SnackbarResult.ActionPerformed) {
                                            openAccessibilityServiceScreen(
                                                context,
                                                LockScreenService::class.java
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        onLongPress = {
                            isHomeStudioVisible = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                }
        ) {
            HomeBackgroundRenderer(
                backgroundName = customizationInfo.equippedBackground,
                scale = customizationInfo.bgScale,
                offsetX = customizationInfo.bgOffsetX,
                offsetY = customizationInfo.bgOffsetY,
                dim = customizationInfo.bgDim,
                customWallpaperPath = customizationInfo.customWallpaperPath
            )
            LocalCustomTheme.current.ThemeObjects(innerPadding)
            Box(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.padding(WindowInsets.statusBarsIgnoringVisibility.asPaddingValues())) {
                    TopBarActions(coins,streak, true, true)

                    Column(
                        Modifier.padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                scaleX = customizationInfo.widgetScale
                                scaleY = customizationInfo.widgetScale
                                translationX = customizationInfo.widgetOffsetX
                                translationY = customizationInfo.widgetOffsetY
                                alpha = customizationInfo.widgetAlpha
                            }
                        ) {
                            viewModel.getHomeWidget()?.invoke(
                                Modifier.size(
                                    200.dp
                                )
                            )
                        }
                        Spacer(Modifier.size(12.dp))

                        val clockFontWeight = when (customizationInfo.clockStyle) {
                            "Light" -> FontWeight.Light
                            "Regular" -> FontWeight.Normal
                            "Bold" -> FontWeight.Bold
                            "Black" -> FontWeight.Black
                            else -> FontWeight.Black
                        }

                        Text(
                            time,
                            fontSize = customizationInfo.clockSizeSp.sp,
                            fontWeight = clockFontWeight,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    isHomeStudioVisible = true
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        )
                        Text(
                            "Today's Quests",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(12.dp))

                        if (questList.isEmpty()) {
                            TextButton(onClick = {
                                navController?.navigate(RootRoute.SelectTemplates.route)
                            }) {
                                Row {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Quests"
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        text = "Add Quests",
                                        fontWeight = FontWeight.ExtraLight,
                                        fontSize = 23.sp
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            questList.forEach { baseQuest ->
                                val isFailed = QuestHelper.isTimeOver(baseQuest)
                                val isCompleted = completedQuests.contains(baseQuest.id)
                                val isHidden = isPrivacyModeEnabled && !isPrivateQuestsUnlocked
                                Text(
                                    text = if (isHidden) "Hidden Quest" else baseQuest.title,
                                    fontWeight = FontWeight.ExtraLight,
                                    fontSize = 23.sp,
                                    color = if (isFailed && !isCompleted) smoothRed else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (isCompleted && !isHidden) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.clickable(
                                        onClick = {
                                            if (isHidden) {
                                                triggerBiometricUnlock {
                                                    isPrivateQuestsUnlocked = true
                                                    navController?.navigate(RootRoute.ViewQuest.route + baseQuest.id)
                                                }
                                            } else {
                                                navController?.navigate(RootRoute.ViewQuest.route + baseQuest.id)
                                            }
                                        },
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false)
                                    )
                                )
                            }
                            Text(
                                text = LocalCustomTheme.current.expandQuestsText,
                                fontWeight = FontWeight.ExtraLight,
                                fontSize = 15.sp,
                                modifier = Modifier.clickable(
                                    onClick = {
                                        isAllQuestsDialogVisible = true
                                    },
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false)
                                )
                            )

                            if (!isSetToDefaultLauncher(context)) {
                                Spacer(Modifier.size(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable(onClick = {
                                        openDefaultLauncherSettings(context)
                                    })
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.baseline_info_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        text = "Set AmniQuest as your default launcher for the best experience",
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = 8.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding() + 8.dp
                        ),
                    horizontalAlignment = Alignment.End
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                color = LocalCustomTheme.current.getExtraColorScheme().toolBoxContainer,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        sidePanelItems.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .size(35.dp)
                                    .clickable(
                                        onClick = { item.onClick() },
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(item.icon),
                                    contentDescription = item.contentDesc,
                                    modifier = Modifier.size(28.dp),
                                    colorFilter = ColorFilter.tint(
                                        LocalCustomTheme.current.getRootColorScheme().primary.copy(alpha = 0.5f),
                                        blendMode = BlendMode.Modulate
                                    )
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 8.dp,
                            bottom = WindowInsets.navigationBars.asPaddingValues()
                                .calculateBottomPadding() + 8.dp
                        ),
                    horizontalAlignment = Alignment.End
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (shortcuts.isEmpty()) {
                            TextButton(onClick = {
                                isAppSelectorVisible = true
                            }) {
                                Row {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Shortcuts"
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        text = "Add Shortcuts",
                                        fontWeight = FontWeight.ExtraLight,
                                        fontSize = 23.sp
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Screentime",
                            fontWeight = FontWeight.ExtraLight,
                            fontSize = 23.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .wrapContentWidth()
                                .combinedClickable(onClick = {
                                    navController?.navigate(RootRoute.ShowScreentimeStats.route)
                                }, onLongClick = {
                                    isAppSelectorVisible = true
                                })
                        )
                        shortcuts.forEach { pkg ->
                            val name = try {
                                val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                                appInfo.loadLabel(context.packageManager).toString()
                            } catch (_: Exception) {
                                pkg
                            }

                            Text(
                                text = name,
                                fontWeight = FontWeight.ExtraLight,
                                fontSize = 23.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .combinedClickable(onClick = {
                                        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                        intent?.let { context.startActivity(it) }
                                    }, onLongClick = {
                                        isAppSelectorVisible = true
                                    })
                            )
                        }
                    }
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = if(navController!=null || LocalCustomTheme.current.docLink == null) "Swipe up" else "Click to read perks",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = swipeIconAnimation.dp)
                        .padding(
                            bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues()
                                .calculateBottomPadding() * 2
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController?.navigate(RootRoute.AppList.route) {
                                launchSingleTop = true
                            }
                        }
                )

            }

        }
    }
}