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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A sleek celestial orbit widget representing daily quest trajectory
 * and focus momentum with glowing orbital nodes.
 */
@Composable
fun QuestOrbitWidget(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val infiniteTransition = rememberInfiniteTransition(label = "quest_orbit_anim")

    // Planetary orbit rotation
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_angle"
    )

    // Inner core pulse
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val orbitRadius = (minOf(size.width, size.height) / 2f) * 0.78f

            // 1. Center Core (Quest Sun / Nucleus)
            drawCircle(
                color = primaryColor.copy(alpha = 0.15f),
                radius = orbitRadius * 0.32f * corePulse,
                center = center
            )
            drawCircle(
                color = primaryColor,
                radius = orbitRadius * 0.16f * corePulse,
                center = center
            )
            drawCircle(
                color = onSurfaceColor,
                radius = orbitRadius * 0.06f,
                center = center
            )

            // 2. Outer Track (Dashed / Segmented Orbit Arc)
            drawCircle(
                color = onSurfaceColor.copy(alpha = 0.2f),
                radius = orbitRadius,
                center = center,
                style = Stroke(width = 1.8f)
            )

            // 3. Inner Resonance Orbit Track
            drawCircle(
                color = primaryColor.copy(alpha = 0.18f),
                radius = orbitRadius * 0.58f,
                center = center,
                style = Stroke(width = 1.2f)
            )

            // 4. Orbiting Celestial Quest Nodes (4 Nodes around circumference)
            val radAngle = Math.toRadians(orbitAngle.toDouble()).toFloat()
            val nodeCount = 4
            for (i in 0 until nodeCount) {
                val nodeAngle = radAngle + (i * (2f * PI.toFloat() / nodeCount))
                val nodeX = center.x + cos(nodeAngle) * orbitRadius
                val nodeY = center.y + sin(nodeAngle) * orbitRadius

                // Connection arc to center
                drawLine(
                    color = primaryColor.copy(alpha = 0.12f),
                    start = center,
                    end = Offset(nodeX, nodeY),
                    strokeWidth = 1f,
                    cap = StrokeCap.Round
                )

                // Outer halo
                drawCircle(
                    color = primaryColor.copy(alpha = 0.35f),
                    radius = 8f,
                    center = Offset(nodeX, nodeY)
                )

                // Solid glowing node
                drawCircle(
                    color = if (i % 2 == 0) primaryColor else secondaryColor,
                    radius = 4.5f,
                    center = Offset(nodeX, nodeY)
                )
            }

            // 5. Active Leading Progress Spark
            val progressAngle = radAngle * 1.5f
            val sparkX = center.x + cos(progressAngle) * (orbitRadius * 0.58f)
            val sparkY = center.y + sin(progressAngle) * (orbitRadius * 0.58f)
            drawCircle(
                color = onSurfaceColor,
                radius = 3.5f,
                center = Offset(sparkX, sparkY)
            )
        }
    }
}
