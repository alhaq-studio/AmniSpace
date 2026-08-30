package com.alhaq.amniquest.app.theme.backgrounds

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

const val WALLPAPER_PRICE = 25
const val CUSTOM_WALLPAPER_PRICE = 50
const val CUSTOM_WALLPAPER_NAME = "Custom Photo"

val availableBackgrounds: List<String> = listOf(
    "Solid Minimal",
    "Pixel Grid",
    "Zen Gradient",
    "Cyber Dune",
    "Nordic Constellation",
    "Sakura Mist",
    "Hacker Matrix"
)

/**
 * Saves a user-selected gallery image to internal app storage for persistent wallpaper use.
 */
fun saveCustomWallpaperFromUri(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val file = File(context.filesDir, "custom_wallpaper_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

/**
 * Universal Home Wallpaper Renderer that applies scale, pan offset, custom bitmap decoding, and dimming overlay.
 */
@Composable
fun HomeBackgroundRenderer(
    backgroundName: String,
    scale: Float = 1.0f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    dim: Float = 0.2f,
    customWallpaperPath: String? = null,
    modifier: Modifier = Modifier
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val customBitmap = remember(customWallpaperPath, backgroundName) {
        if (backgroundName == CUSTOM_WALLPAPER_NAME && !customWallpaperPath.isNullOrBlank()) {
            try {
                val file = File(customWallpaperPath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else null
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        // Transformed Art Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        ) {
            when {
                backgroundName == CUSTOM_WALLPAPER_NAME && customBitmap != null -> {
                    Image(
                        bitmap = customBitmap,
                        contentDescription = "Custom Wallpaper",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                backgroundName == "Pixel Grid" -> PixelGridBackground(primaryColor, onSurfaceColor)
                backgroundName == "Zen Gradient" -> ZenGradientBackground(surfaceColor, primaryColor, secondaryColor)
                backgroundName == "Cyber Dune" -> CyberDuneBackground(primaryColor, onSurfaceColor)
                backgroundName == "Nordic Constellation" -> ConstellationBackground(primaryColor, onSurfaceColor)
                backgroundName == "Sakura Mist" -> SakuraMistBackground(primaryColor, secondaryColor)
                backgroundName == "Hacker Matrix" -> HackerMatrixBackground(primaryColor)
                else -> { /* Solid Minimal: pure surface color */ }
            }
        }

        // Dimming & Contrast Scrim Overlay
        if (dim > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.85f)))
            )
        }
    }
}

@Composable
fun PixelGridBackground(primaryColor: Color, onSurfaceColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pixel_bg")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val spacing = 28f
        val cols = (size.width / spacing).toInt() + 1
        val rows = (size.height / spacing).toInt() + 1

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = c * spacing
                val y = r * spacing
                val isAccent = (r + c) % 7 == 0
                val dotColor = if (isAccent) primaryColor.copy(alpha = pulse * 0.4f) else onSurfaceColor.copy(alpha = 0.06f)
                val dotSize = if (isAccent) 2.5f else 1.5f

                drawRect(
                    color = dotColor,
                    topLeft = Offset(x, y),
                    size = Size(dotSize, dotSize)
                )
            }
        }
    }
}

@Composable
fun ZenGradientBackground(surface: Color, primary: Color, secondary: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.18f),
                        secondary.copy(alpha = 0.08f),
                        surface
                    ),
                    radius = 1200f
                )
            )
    )
}

@Composable
fun CyberDuneBackground(primaryColor: Color, onSurfaceColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dune_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dune_phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val lineCount = 8

        for (k in 0 until lineCount) {
            val path = Path()
            val baseY = height * 0.45f + (k * (height * 0.07f))
            val amplitude = 35f + (k * 6f)
            val steps = 40

            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * width
                val normX = (i.toFloat() / steps) * (2f * PI.toFloat())
                val y = baseY + sin(normX * 1.2f + phase + (k * 0.4f)) * amplitude

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = primaryColor.copy(alpha = 0.06f + (k * 0.015f)),
                style = Stroke(width = 1.6f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun ConstellationBackground(primaryColor: Color, onSurfaceColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val stars = listOf(
            Offset(width * 0.18f, height * 0.15f),
            Offset(width * 0.42f, height * 0.12f),
            Offset(width * 0.78f, height * 0.22f),
            Offset(width * 0.85f, height * 0.48f),
            Offset(width * 0.62f, height * 0.65f),
            Offset(width * 0.25f, height * 0.72f),
            Offset(width * 0.15f, height * 0.45f)
        )

        // Draw connecting filaments
        for (i in 0 until stars.size - 1) {
            drawLine(
                color = primaryColor.copy(alpha = 0.09f),
                start = stars[i],
                end = stars[i + 1],
                strokeWidth = 1f
            )
        }

        // Draw star nodes
        for (star in stars) {
            drawCircle(
                color = onSurfaceColor.copy(alpha = 0.35f),
                radius = 2.5f,
                center = star
            )
        }
    }
}

@Composable
fun SakuraMistBackground(primaryColor: Color, secondaryColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "sakura_drift")
    val drift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sakura_drift"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val petalCount = 12
        for (i in 0 until petalCount) {
            val progress = (drift + (i.toFloat() / petalCount)) % 1f
            val x = (size.width * ((i * 0.17f + progress * 0.3f) % 1f))
            val y = size.height * progress
            val alpha = (sin(progress * PI.toFloat())).toFloat() * 0.25f

            drawCircle(
                color = primaryColor.copy(alpha = alpha),
                radius = 3.5f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun HackerMatrixBackground(primaryColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "matrix_rain")
    val rainPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rain_phase"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val colCount = 18
        val colWidth = size.width / colCount

        for (c in 0 until colCount) {
            val speed = 1f + ((c % 5) * 0.25f)
            val dropProgress = (rainPhase * speed + (c * 0.13f)) % 1f
            val startY = size.height * dropProgress
            val dashLength = 22f

            drawLine(
                color = primaryColor.copy(alpha = 0.18f),
                start = Offset(c * colWidth + (colWidth / 2f), startY),
                end = Offset(c * colWidth + (colWidth / 2f), startY + dashLength),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }
    }
}
