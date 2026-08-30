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
 * An 8-bit pixel-art hourglass widget symbolizing the conscious flow of time.
 */
@Composable
fun PixelHourglassWidget(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = onSurfaceColor.copy(alpha = 0.85f)
    val sandColor = primaryColor
    val highlightColor = onSurfaceColor.copy(alpha = 0.4f)

    val infiniteTransition = rememberInfiniteTransition(label = "hourglass_anim")

    // Sand falling stream animation
    val sandProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sand_progress"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_glow"
    )

    // 16x16 Pixel Hourglass Outline Template (O: Outline, H: Glass Highlight)
    val frameTemplate = remember {
        arrayOf(
            "1111111111111111",
            "1..............1",
            ".1.SSSSSSSSSS.1.",
            "..1.SSSSSSSS.1..",
            "...1.SSSSSS.1...",
            "....1.SSSS.1....",
            ".....1.SS.1.....",
            "......1..1......",
            "......1..1......",
            ".....1.SS.1.....",
            "....1.SSSS.1....",
            "...1.SSSSSS.1...",
            "..1.SSSSSSSS.1..",
            ".1.SSSSSSSSSS.1.",
            "1..............1",
            "1111111111111111"
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
            val startY = (canvasHeight - (gridSize * pixelSize)) / 2f

            // 1. Draw outer glass frame
            for (row in 0 until gridSize) {
                val rowString = frameTemplate.getOrNull(row) ?: continue
                for (col in 0 until minOf(gridSize, rowString.length)) {
                    val char = rowString[col]
                    if (char == '1') {
                        drawRect(
                            color = outlineColor,
                            topLeft = Offset(startX + col * pixelSize, startY + row * pixelSize),
                            size = Size(pixelSize, pixelSize)
                        )
                    }
                }
            }

            // 2. Draw Top Chamber Sand (Depleting level based on pulseGlow)
            val topSandRows = listOf(
                2 to (3..12),
                3 to (4..11),
                4 to (5..10),
                5 to (6..9),
                6 to (7..8)
            )

            for ((row, colRange) in topSandRows) {
                for (col in colRange) {
                    drawRect(
                        color = sandColor.copy(alpha = 0.9f),
                        topLeft = Offset(startX + col * pixelSize, startY + row * pixelSize),
                        size = Size(pixelSize, pixelSize)
                    )
                }
            }

            // 3. Draw Falling Stream Grains
            val streamGrainCount = 3
            for (i in 0 until streamGrainCount) {
                val grainOffset = (sandProgress + (i.toFloat() / streamGrainCount)) % 1f
                val grainRow = 7f + (grainOffset * 4f)
                val grainCol = 7.5f + (sin(grainOffset * 3.1415f) * 0.2f)

                drawRect(
                    color = sandColor,
                    topLeft = Offset(startX + grainCol * pixelSize, startY + grainRow * pixelSize),
                    size = Size(pixelSize * 0.9f, pixelSize * 0.9f)
                )
            }

            // 4. Draw Bottom Accumulating Sand Pile
            val bottomSandRows = listOf(
                13 to (3..12),
                12 to (4..11),
                11 to (5..10),
                10 to (6..9),
                9 to (7..8)
            )

            for ((row, colRange) in bottomSandRows) {
                for (col in colRange) {
                    drawRect(
                        color = sandColor.copy(alpha = 0.85f),
                        topLeft = Offset(startX + col * pixelSize, startY + row * pixelSize),
                        size = Size(pixelSize, pixelSize)
                    )
                }
            }

            // 5. Ambient sparkle / time aura
            val auraX = startX + 2 * pixelSize
            val auraY = startY + 2 * pixelSize
            drawRect(
                color = highlightColor.copy(alpha = pulseGlow * 0.6f),
                topLeft = Offset(auraX, auraY),
                size = Size(pixelSize, pixelSize)
            )
        }
    }
}
