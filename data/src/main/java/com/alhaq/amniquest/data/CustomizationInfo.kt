package com.alhaq.amniquest.data

import kotlinx.serialization.Serializable

@Serializable
data class CustomizationInfo(
    var purchasedThemes: HashSet<String> = hashSetOf("Pitch Black"),
    var equippedTheme: String = "Pitch Black",
    var themeData: MutableMap<String, String> = mutableMapOf(),

    var purchasedWidgets: HashSet<String> = hashSetOf("Heat Map", "Pixel Familiar"),
    var equippedWidget: String = "Heat Map",

    var purchasedBackgrounds: HashSet<String> = hashSetOf("Solid Minimal", "Pixel Grid"),
    var equippedBackground: String = "Solid Minimal",
    var bgScale: Float = 1.0f,
    var bgOffsetX: Float = 0f,
    var bgOffsetY: Float = 0f,
    var bgDim: Float = 0.2f,

    var clockStyle: String = "Bold",
    var clockSizeSp: Float = 42f,
    var is24Hr: Boolean = false,
    var textFont: String = "Default",

    var widgetScale: Float = 1.0f,
    var widgetOffsetX: Float = 0f,
    var widgetOffsetY: Float = 0f,
    var widgetAlpha: Float = 1.0f,

    var hasUnlockedCustomWallpaper: Boolean = false,
    var customWallpaperPath: String? = null,
    var isSupporter: Boolean = false,
    var supporterTier: String? = null,
    var supporterExpiry: Long = 0L,
    var offlineLicenseKey: String? = null,
)