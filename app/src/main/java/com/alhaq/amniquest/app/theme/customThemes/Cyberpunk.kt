package com.alhaq.amniquest.app.theme.customThemes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.alhaq.amniquest.app.theme.data.CustomColor

class CyberpunkTheme(): BaseTheme {
    override fun getRootColorScheme(): ColorScheme {
        return darkColorScheme(
            primary = Color(0xFF00F0FF),       // Electric Cyan
            onPrimary = Color(0xFF0D0B18),     // Dark contrast
            secondary = Color(0xFFFF007F),     // Neon Magenta
            onSecondary = Color(0xFF0D0B18),
            tertiary = Color(0xFF9D4EDD),      // Arcade Violet
            onTertiary = Color.White,
            background = Color(0xFF0D0B18),    // Deep Dark Violet
            onBackground = Color(0xFF00F0FF),  // Neon text
            surface = Color(0xFF161224),       // Card surface
            onSurface = Color(0xFFE2E8F0),     // Soft white
            error = Color(0xFFFF2A6D),         // Cyberpunk warning
            onError = Color.Black
        )
    }

    override fun getExtraColorScheme(): CustomColor {
        return CustomColor(
            toolBoxContainer = Color(0xFF201936),
            heatMapCells = Color(0xFF00F0FF),
            dialogText = Color.White
        )
    }

    @Composable
    override fun ThemeObjects(innerPadding: PaddingValues) {
        null
    }

    override val name: String
        get() = "Cyberpunk"

    override val description: String
        get() = "Welcome to Night City"

    override val expandQuestsText: String
        get() = "⚡⚡⚡⚡⚡⚡⚡"
}
