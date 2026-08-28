package com.alhaq.amniquest.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.alhaq.amniquest.app.theme.customThemes.BaseTheme
import com.alhaq.amniquest.app.theme.customThemes.PitchBlackTheme

val pitchBlackTheme = PitchBlackTheme()
val LocalCustomTheme = staticCompositionLocalOf <BaseTheme>{ pitchBlackTheme }

@Composable
fun LauncherTheme(
    customTheme: BaseTheme,
    darkTheme: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {

    CompositionLocalProvider(
        LocalCustomTheme provides customTheme,
    ) {
        MaterialTheme(
            colorScheme = customTheme.getRootColorScheme(),
            typography = customTypography,
            content = content
        )
    }
}