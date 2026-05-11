package com.codestudio71.spoticious.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max

private val GradientGreenToRed =
    Brush.verticalGradient(
        listOf(Color(0xFFFF1744), Color(0xFFFFD600), Color(0xFF00FF88)),
    )

@Composable
fun DbfsMeter(
    currentDbfs: Float,
    peakDbfs: Float,
    modifier: Modifier = Modifier,
) {
    val clipped = currentDbfs.coerceIn(-60f, 0f)
    val label =
        String.format(Locale.US, "%.1f", clipped)

    Column(
        modifier =
            modifier
                .width(28.dp)
                .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (currentDbfs > -1f) Color(0xFFFF1744) else Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val radius = CornerRadius(minOf(10f, w / 4f), minOf(10f, w / 4f))
            val track = Color(0xFF0D0818)
            drawRoundRect(color = track, size = Size(w, h), cornerRadius = radius)

            val frac = ((clipped + 60f) / 60f).coerceIn(0f, 1f)
            val fillH = max(0f, h * frac)
            if (fillH > 2f) {
                drawRoundRect(
                    brush = GradientGreenToRed,
                    topLeft = Offset(0f, h - fillH),
                    size = Size(w, fillH),
                    cornerRadius = CornerRadius(minOf(radius.x, fillH / 2f), minOf(radius.y, fillH / 2f)),
                )
            }

            val peakFrac = ((peakDbfs.coerceIn(-60f, 0f)) + 60f) / 60f
            val yPeak = (h - peakFrac * h).coerceIn(0f, h)
            val strokePx = 2.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = 0.92f),
                start = Offset(0f, yPeak),
                end = Offset(w, yPeak),
                strokeWidth = strokePx,
            )
        }
    }
}
