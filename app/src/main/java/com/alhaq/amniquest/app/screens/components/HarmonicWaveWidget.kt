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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

/**
 * A harmonic sine frequency waveform widget representing dynamic balance
 * and focus resonance.
 */
@Composable
fun HarmonicWaveWidget(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")

    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_1"
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_2"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val amplitude = height * 0.22f

            // 1. Center Guide Axis (Faint Line)
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.12f),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1f
            )

            // 2. Primary Waveform Path
            val path1 = Path()
            val steps = 60
            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * width
                val normalizedX = (i.toFloat() / steps) * (2f * PI.toFloat())
                val y = centerY + sin(normalizedX * 1.5f + wavePhase1) * amplitude

                if (i == 0) path1.moveTo(x, y) else path1.lineTo(x, y)
            }

            drawPath(
                path = path1,
                color = primaryColor.copy(alpha = 0.85f),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
            )

            // 3. Secondary Intersecting Waveform Path
            val path2 = Path()
            for (i in 0..steps) {
                val x = (i.toFloat() / steps) * width
                val normalizedX = (i.toFloat() / steps) * (2f * PI.toFloat())
                val y = centerY + sin(normalizedX * 2f + wavePhase2) * (amplitude * 0.75f)

                if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
            }

            drawPath(
                path = path2,
                color = secondaryColor.copy(alpha = 0.55f),
                style = Stroke(width = 2f, cap = StrokeCap.Round)
            )

            // 4. Subtle Ambient Harmonic Nodes at peaks
            val peakCount = 5
            for (k in 1 until peakCount) {
                val nodeX = (k.toFloat() / peakCount) * width
                val nodeNormX = (k.toFloat() / peakCount) * (2f * PI.toFloat())
                val nodeY = centerY + sin(nodeNormX * 1.5f + wavePhase1) * amplitude

                drawCircle(
                    color = onSurfaceColor,
                    radius = 3f,
                    center = Offset(nodeX, nodeY)
                )
            }
        }
    }
}
