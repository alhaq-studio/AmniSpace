package neth.iecal.questphone.app.theme.customThemes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import neth.iecal.questphone.app.theme.data.CustomColor

class NordicFrostTheme(): BaseTheme {
    override fun getRootColorScheme(): ColorScheme {
        return darkColorScheme(
            primary = Color(0xFF38BDF8),       // Ice Sky Blue
            onPrimary = Color(0xFF0F172A),     // Deep slate text
            secondary = Color(0xFF7DD3FC),     // Soft Glacier
            onSecondary = Color(0xFF0F172A),
            tertiary = Color(0xFFA5F3FC),      // Frost Teal
            onTertiary = Color(0xFF0F172A),
            background = Color(0xFF0F172A),    // Deep Slate Navy
            onBackground = Color(0xFFF8FAFC),  // Pure ice text
            surface = Color(0xFF1E293B),       // Slate surface
            onSurface = Color(0xFFF1F5F9),
            error = Color(0xFFF87171),
            onError = Color.Black
        )
    }

    override fun getExtraColorScheme(): CustomColor {
        return CustomColor(
            toolBoxContainer = Color(0xFF334155),
            heatMapCells = Color(0xFF38BDF8),
            dialogText = Color.White
        )
    }

    @Composable
    override fun ThemeObjects(innerPadding: PaddingValues) {
        null
    }

    override val name: String
        get() = "Nordic Frost"

    override val description: String
        get() = "Arctic Clarity & Focus"

    override val expandQuestsText: String
        get() = "❄️❄️❄️❄️❄️❄️❄️"
}
