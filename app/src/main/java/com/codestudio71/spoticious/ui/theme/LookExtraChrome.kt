package com.codestudio71.spoticious.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp

/**
 * Atmosphere overlays for Extra + Extra submenu hosts.
 * Classic Miami / Sun / Spotify / Clear: no-op (base Extra stays untouched).
 */
@Composable
fun LookExtraAtmosphere(modifier: Modifier = Modifier) {
    val look = LocalSpoticiousLook.current
    when (look.id) {
        SpoticiousLookId.FUTURE_CRYPTO -> CryptoAtmosphere(modifier)
        SpoticiousLookId.MIAMI_2 -> Miami2Atmosphere(modifier)
        else -> Unit
    }
}

/** True when Extra should use upgraded chrome (not classic Miami). */
val SpoticiousLook.usesPremiumExtraChrome: Boolean
    get() = id == SpoticiousLookId.MIAMI_2 || id == SpoticiousLookId.FUTURE_CRYPTO

@Composable
fun LookExtraHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LookExtraAtmosphere(modifier = Modifier.fillMaxSize())
        content()
    }
}

@Composable
private fun CryptoAtmosphere(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Purple neo bloom (top-right)
        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(0xFFBD00FF).copy(alpha = 0.32f),
                            Color(0xFF5A00A8).copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                    center = Offset(size.width * 0.92f, size.height * 0.08f),
                    radius = size.minDimension * 0.95f,
                ),
        )
        // Cyan bloom (bottom-left)
        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(0xFF00E5FF).copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    center = Offset(size.width * 0.08f, size.height * 0.88f),
                    radius = size.minDimension * 0.85f,
                ),
        )
        // Deep purple vignette
        drawRect(
            brush =
                Brush.verticalGradient(
                    colors =
                        listOf(
                            Color(0xFF050010).copy(alpha = 0.55f),
                            Color.Transparent,
                            Color(0xFF120028).copy(alpha = 0.7f),
                        ),
                ),
        )
        // Faint cyber mesh
        val step = 48.dp.toPx()
        val line = Color(0xFFBD00FF).copy(alpha = 0.08f)
        val node = Color(0xFF00E5FF).copy(alpha = 0.14f)
        var x = 0f
        while (x < size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        x = step / 2f
        while (x < size.width) {
            y = step / 2f
            while (y < size.height) {
                drawCircle(node, radius = 1.6f, center = Offset(x, y))
                y += step
            }
            x += step
        }
        drawLine(
            color = Color(0xFF00E5FF).copy(alpha = 0.1f),
            start = Offset(0f, size.height * 0.22f),
            end = Offset(size.width, size.height * 0.18f),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 14f)),
        )
    }
}

@Composable
private fun Miami2Atmosphere(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Cool glass sheen
        drawRect(
            brush =
                Brush.verticalGradient(
                    colors =
                        listOf(
                            Color(0xFF5CE1FF).copy(alpha = 0.16f),
                            Color.Transparent,
                            Color(0xFFFF4FA0).copy(alpha = 0.12f),
                        ),
                ),
        )
        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color.White.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                    center = Offset(size.width * 0.5f, 0f),
                    radius = size.minDimension * 0.9f,
                ),
        )
        drawRect(
            brush =
                Brush.radialGradient(
                    colors =
                        listOf(
                            Color(0xFF1A3A58).copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                    center = Offset(size.width * 0.12f, size.height * 0.72f),
                    radius = size.minDimension * 0.75f,
                ),
        )
    }
}
