package com.alhaq.amniquest.app.screens.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.sin

/**
 * An 8-bit pixel-art companion widget with smooth retro idle animation
 * and ambient floating particles.
 */
@Composable
fun PixelCompanionWidget(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val infiniteTransition = rememberInfiniteTransition(label = "pixel_companion_anim")

    // Bobbing / breathing animation
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bob_offset"
    )

    // Frame switch (for 3-frame idle animation)
    val framePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "frame_phase"
    )

    val currentFrame = remember(framePhase) { framePhase.toInt() % 4 }

    // 16x16 Pixel Art Spirit Matrix (0: empty, 1: primary, 2: core, 3: eye/sparkle)
    val frame0 = remember {
        arrayOf(
            "....11..........",
            "...1111..1......",
            "..112211.11.....",
            "..122221.121....",
            ".112222111221...",
            ".122222212221...",
            ".123223211221...",
            ".12322321121....",
            ".1222222111.....",
            "..12222211......",
            "..1222221.......",
            "...122211.......",
            "...11221........",
            "....1111........",
            ".....11.........",
            "................"
        )
    }

    val frame1 = remember {
        arrayOf(
            ".....11.........",
            "....1111.1......",
            "...11221111.....",
            "..1122221221....",
            ".11222221221....",
            ".12222221121....",
            ".1232232111.....",
            ".123223211......",
            ".12222221.......",
            "..1222221.......",
            "..1222221.1.....",
            "...12221111.....",
            "....122211......",
            "....1111........",
            ".....11.........",
            "................"
        )
    }

    val frame2 = remember {
        arrayOf(
            "...11...........",
            "..1111...1......",
            "..12211..11.....",
            ".1122211.121....",
            ".122222111221...",
            ".122222212221...",
            ".121221211221...",
            ".12122121121....",
            ".1222222111.....",
            "..12222211......",
            "..1222221.......",
            "...122211.......",
            "...11221........",
            "....1111........",
            ".....11.........",
            "................"
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val gridSize = 16
            val pixelSize = (minOf(canvasWidth, canvasHeight) * 0.72f) / gridSize

            val startX = (canvasWidth - (gridSize * pixelSize)) / 2f
            val verticalFloat = sin(bobOffset) * (pixelSize * 0.8f)
            val startY = ((canvasHeight - (gridSize * pixelSize)) / 2f) + verticalFloat

            val activeGrid = when (currentFrame) {
                0, 2 -> frame0
                1 -> frame1
                else -> frame2
            }

            // Draw floating ambient sparkles
            val sparkleCount = 6
            for (i in 0 until sparkleCount) {
                val sparkPhase = (bobOffset + i * 1.05f) % 6.28318f
                val sparkX = startX + (sin(sparkPhase * 1.5f + i) * 0.45f + 0.5f) * (gridSize * pixelSize)
                val sparkY = startY + (sin(sparkPhase * 2f + i) * 0.45f + 0.5f) * (gridSize * pixelSize) - (sparkPhase * 3f)
                val sparkAlpha = (sin(sparkPhase) * 0.5f + 0.5f).coerceIn(0.1f, 0.85f)

                drawRect(
                    color = secondaryColor.copy(alpha = sparkAlpha),
                    topLeft = Offset(sparkX, sparkY),
                    size = Size(pixelSize * 0.8f, pixelSize * 0.8f)
                )
            }

            // Draw pixel grid
            for (row in 0 until gridSize) {
                val rowString = activeGrid.getOrNull(row) ?: continue
                for (col in 0 until minOf(gridSize, rowString.length)) {
                    val char = rowString[col]
                    val color: Color? = when (char) {
                        '1' -> primaryColor
                        '2' -> primaryColor.copy(alpha = 0.7f)
                        '3' -> onSurfaceColor
                        else -> null
                    }

                    if (color != null) {
                        drawRect(
                            color = color,
                            topLeft = Offset(startX + col * pixelSize, startY + row * pixelSize),
                            size = Size(pixelSize, pixelSize)
                        )
                    }
                }
            }
        }
    }
}
