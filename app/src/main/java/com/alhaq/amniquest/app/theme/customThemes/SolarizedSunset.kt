package com.alhaq.amniquest.app.theme.customThemes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.alhaq.amniquest.app.theme.data.CustomColor

class SolarizedSunsetTheme(): BaseTheme {
    override fun getRootColorScheme(): ColorScheme {
        return darkColorScheme(
            primary = Color(0xFFF59E0B),       // Warm Amber
            onPrimary = Color(0xFF1C1917),     // Stone text
            secondary = Color(0xFFF97316),     // Sunset Orange
            onSecondary = Color(0xFF1C1917),
            tertiary = Color(0xFFD97706),      // Terracotta Gold
            onTertiary = Color.White,
            background = Color(0xFF1C1917),    // Deep Warm Stone
            onBackground = Color(0xFFFEF3C7),  // Soft Cream Text
            surface = Color(0xFF292524),       // Warm Stone Surface
            onSurface = Color(0xFFFDE68A),
            error = Color(0xFFEF4444),
            onError = Color.Black
        )
    }

    override fun getExtraColorScheme(): CustomColor {
        return CustomColor(
            toolBoxContainer = Color(0xFF44403C),
            heatMapCells = Color(0xFFF59E0B),
            dialogText = Color.White
        )
    }

    @Composable
    override fun ThemeObjects(innerPadding: PaddingValues) {
        null
    }

    override val name: String
        get() = "Solarized Sunset"

    override val description: String
        get() = "Warm Amber Oasis"

    override val expandQuestsText: String
        get() = "🌅🌅🌅🌅🌅🌅🌅"
}
