package com.alhaq.amniquest.app.screens.launcher.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alhaq.amniquest.app.theme.backgrounds.CUSTOM_WALLPAPER_NAME
import com.alhaq.amniquest.app.theme.backgrounds.saveCustomWallpaperFromUri
import com.alhaq.amniquest.data.CustomizationInfo
import com.alhaq.amniquest.homeWidgets
import com.alhaq.amniquest.themes

@Composable
fun HomeStudioDialog(
    customizationInfo: CustomizationInfo,
    onDismiss: () -> Unit,
    onSave: (CustomizationInfo) -> Unit,
    onOpenStore: () -> Unit,
    onOpenCustomize: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Wallpaper / Background State
    var selectedBg by remember { mutableStateOf(customizationInfo.equippedBackground) }
    var customWallpaperPath by remember { mutableStateOf(customizationInfo.customWallpaperPath) }
    var bgScale by remember { mutableFloatStateOf(customizationInfo.bgScale) }
    var bgOffsetX by remember { mutableFloatStateOf(customizationInfo.bgOffsetX) }
    var bgOffsetY by remember { mutableFloatStateOf(customizationInfo.bgOffsetY) }
    var bgDim by remember { mutableFloatStateOf(customizationInfo.bgDim) }

    // Widget State
    var selectedWidget by remember { mutableStateOf(customizationInfo.equippedWidget) }
    var widgetScale by remember { mutableFloatStateOf(customizationInfo.widgetScale) }
    var widgetOffsetX by remember { mutableFloatStateOf(customizationInfo.widgetOffsetX) }
    var widgetOffsetY by remember { mutableFloatStateOf(customizationInfo.widgetOffsetY) }
    var widgetAlpha by remember { mutableFloatStateOf(customizationInfo.widgetAlpha) }

    // Theme State
    var selectedTheme by remember { mutableStateOf(customizationInfo.equippedTheme) }

    // Clock State
    var is24Hr by remember { mutableStateOf(customizationInfo.is24Hr) }
    var clockSize by remember { mutableFloatStateOf(customizationInfo.clockSizeSp) }
    var clockStyle by remember { mutableStateOf(customizationInfo.clockStyle) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val path = saveCustomWallpaperFromUri(context, uri)
            if (path != null) {
                customWallpaperPath = path
                selectedBg = CUSTOM_WALLPAPER_NAME
            }
        }
    }

    val tabs = listOf("Wallpapers", "Widget", "Theme", "Clock", "Studio")

    // Only display purchased items owned by user
    val ownedBackgrounds = customizationInfo.purchasedBackgrounds.toList()
    val ownedWidgets = customizationInfo.purchasedWidgets.toList().filter { homeWidgets.containsKey(it) }
    val ownedThemes = customizationInfo.purchasedThemes.toList().filter { themes.containsKey(it) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Home Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Contents (Scrollable)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> {
                            // Wallpapers Tab (Only Owned Wallpapers)
                            Text(
                                text = "Unlocked Wallpapers",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(ownedBackgrounds) { bgName ->
                                    val isSelected = bgName == selectedBg
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { selectedBg = bgName }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = bgName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                item {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                onDismiss()
                                                onOpenStore()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Store",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Get More",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            if (selectedBg == CUSTOM_WALLPAPER_NAME) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Choose Photo from Gallery")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Zoom / Scale Slider
                            Text(
                                text = "Zoom / Scale: ${(bgScale * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = bgScale,
                                onValueChange = { bgScale = it },
                                valueRange = 0.5f..2.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            // Pan Horizontal
                            Text(
                                text = "Pan X: ${bgOffsetX.toInt()}px",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = bgOffsetX,
                                onValueChange = { bgOffsetX = it },
                                valueRange = -300f..300f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            // Dimming Scrim
                            Text(
                                text = "Dimming / Contrast: ${(bgDim * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = bgDim,
                                onValueChange = { bgDim = it },
                                valueRange = 0.0f..0.8f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        1 -> {
                            // Widget Tab (Only Owned Widgets)
                            Text(
                                text = "Unlocked Home Widgets",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ownedWidgets) { wName ->
                                    val isSelected = wName == selectedWidget
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { selectedWidget = wName }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = wName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                item {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                onDismiss()
                                                onOpenStore()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Store",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Get More",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Widget Resizing / Scale
                            Text(
                                text = "Widget Scale / Size: ${(widgetScale * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = widgetScale,
                                onValueChange = { widgetScale = it },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            // Widget Transparency / Opacity
                            Text(
                                text = "Transparency / Opacity: ${(widgetAlpha * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = widgetAlpha,
                                onValueChange = { widgetAlpha = it },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            // Widget Position Pan X
                            Text(
                                text = "Position Pan X: ${widgetOffsetX.toInt()}px",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = widgetOffsetX,
                                onValueChange = { widgetOffsetX = it },
                                valueRange = -200f..200f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            // Widget Position Pan Y
                            Text(
                                text = "Position Pan Y: ${widgetOffsetY.toInt()}px",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = widgetOffsetY,
                                onValueChange = { widgetOffsetY = it },
                                valueRange = -200f..200f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Reset Widget Position Button
                            OutlinedButton(
                                onClick = {
                                    widgetScale = 1.0f
                                    widgetOffsetX = 0f
                                    widgetOffsetY = 0f
                                    widgetAlpha = 1.0f
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset Widget Position & Scale")
                            }
                        }

                        2 -> {
                            // Theme Tab (Only Owned Themes)
                            Text(
                                text = "Unlocked Themes",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ownedThemes) { tName ->
                                    val isSelected = tName == selectedTheme
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { selectedTheme = tName }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = tName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                item {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .clickable {
                                                onDismiss()
                                                onOpenStore()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Store",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Get More",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        3 -> {
                            // Clock & Time Tab
                            Text(
                                text = "Time Format",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { is24Hr = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (!is24Hr) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                                    )
                                ) {
                                    Text("12-Hour")
                                }
                                OutlinedButton(
                                    onClick = { is24Hr = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (is24Hr) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                                    )
                                ) {
                                    Text("24-Hour")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Clock Size
                            Text(
                                text = "Clock Size: ${clockSize.toInt()}sp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = clockSize,
                                onValueChange = { clockSize = it },
                                valueRange = 24f..64f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Clock Style / Weight
                            Text(
                                text = "Clock Weight",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val styles = listOf("Light", "Regular", "Bold", "Black")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(styles) { style ->
                                    val isSelected = style == clockStyle
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable { clockStyle = style }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = style,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        4 -> {
                            // Studio Shortcuts & Store
                            Text(
                                text = "Studio Shortcuts",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenCustomize()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Customize Themes & Widgets")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenStore()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Store",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock More in Store")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Apply / Save Button
                Button(
                    onClick = {
                        val updated = customizationInfo.copy(
                            equippedBackground = selectedBg,
                            customWallpaperPath = customWallpaperPath,
                            bgScale = bgScale,
                            bgOffsetX = bgOffsetX,
                            bgOffsetY = bgOffsetY,
                            bgDim = bgDim,
                            equippedWidget = selectedWidget,
                            widgetScale = widgetScale,
                            widgetOffsetX = widgetOffsetX,
                            widgetOffsetY = widgetOffsetY,
                            widgetAlpha = widgetAlpha,
                            equippedTheme = selectedTheme,
                            is24Hr = is24Hr,
                            clockSizeSp = clockSize,
                            clockStyle = clockStyle
                        )
                        onSave(updated)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Apply",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
