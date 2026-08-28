package com.alhaq.amniquest

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alhaq.amniquest.app.screens.components.NeuralMeshAsymmetrical
import com.alhaq.amniquest.app.screens.components.NeuralMeshSymmetrical
import com.alhaq.amniquest.app.screens.quest.stats.components.HeatMapHomeScreenWrapper
import com.alhaq.amniquest.app.theme.customThemes.BaseTheme
import com.alhaq.amniquest.app.theme.customThemes.BonsaiTheme
import com.alhaq.amniquest.app.theme.customThemes.CherryBlossomsTheme
import com.alhaq.amniquest.app.theme.customThemes.CyberpunkTheme
import com.alhaq.amniquest.app.theme.customThemes.HackerTheme
import com.alhaq.amniquest.app.theme.customThemes.NordicFrostTheme
import com.alhaq.amniquest.app.theme.customThemes.PitchBlackTheme
import com.alhaq.amniquest.app.theme.customThemes.SolarizedSunsetTheme

val themes: Map<String, BaseTheme> = mapOf(
    "Cherry Blossoms" to CherryBlossomsTheme(),
    "Hacker" to HackerTheme(),
    "Pitch Black" to PitchBlackTheme(),
    "Bonsai" to BonsaiTheme(),
    "Cyberpunk" to CyberpunkTheme(),
    "Nordic Frost" to NordicFrostTheme(),
    "Solarized Sunset" to SolarizedSunsetTheme()
)

const val HOME_WIDGET_PRICE = 30
var homeWidgets: Map<String, @Composable (Modifier)-> Unit> = mapOf(
    "Heat Map" to { HeatMapHomeScreenWrapper(it) },
    "Neural Mesh Symmetrical" to { NeuralMeshSymmetrical(it) },
    "Neural Mesh ASymmetrical" to { NeuralMeshAsymmetrical(it) },
)