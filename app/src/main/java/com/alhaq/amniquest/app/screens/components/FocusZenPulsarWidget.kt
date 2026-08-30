package com.alhaq.amniquest.app.screens.components

import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A minimalist Zen Pulsar widget that pulses in harmony with a 4-second
 * box breathing cadence to promote calm, mindful phone interactions.
 */
@Composable
fun FocusZenPulsarWidget(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    val infiniteTransition = rememberInfiniteTransition(label = "zen_pulsar_anim")

    // 4-second Inhale/Hold/Exhale/Rest breathing curve
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    // Continuous smooth rotation for dynamic geometry
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = (minOf(size.width, size.height) / 2f) * 0.88f
            val baseRadius = maxRadius * breathScale

            // 1. Center calm core
            drawCircle(
                color = primaryColor.copy(alpha = 0.85f),
                radius = maxRadius * 0.12f * breathScale,
                center = center
            )

            // 2. Inner pulsating ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.4f),
                radius = baseRadius * 0.35f,
                center = center,
                style = Stroke(width = 2.5f)
            )

            // 3. Middle harmonic ring with breathing opacity
            drawCircle(
                color = onSurfaceColor.copy(alpha = 0.25f * breathScale),
                radius = baseRadius * 0.65f,
                center = center,
                style = Stroke(width = 2f)
            )

            // 4. Outer boundary ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.5f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 3f)
            )

            // 5. Orbital harmonic petal arcs (4 symmetry nodes)
            val nodeCount = 6
            val radAngle = Math.toRadians(rotationAngle.toDouble()).toFloat()

            for (i in 0 until nodeCount) {
                val angle = radAngle + (i * (2f * PI.toFloat() / nodeCount))
                val nodeX = center.x + cos(angle) * (baseRadius * 0.65f)
                val nodeY = center.y + sin(angle) * (baseRadius * 0.65f)

                // Node dot
                drawCircle(
                    color = onSurfaceColor.copy(alpha = 0.7f),
                    radius = 3.5f,
                    center = Offset(nodeX, nodeY)
                )

                // Connecting fine filaments to center
                drawLine(
                    color = primaryColor.copy(alpha = 0.18f),
                    start = center,
                    end = Offset(nodeX, nodeY),
                    strokeWidth = 1.2f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
