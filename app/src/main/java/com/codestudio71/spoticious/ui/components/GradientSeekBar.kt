package com.codestudio71.spoticious.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import kotlin.math.roundToLong

/**
 * Seek bar with look-aware inactive track + solid or gradient active fill + accent thumb.
 * Used by MiniPlayer (and available for FullPlayer).
 */
@Composable
fun GradientSeekBar(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 3.dp,
    thumbRadius: Dp = 6.dp,
) {
    val look = LocalSpoticiousLook.current
    val p = progress.coerceIn(0f, 1f)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(trackHeight + thumbRadius * 2)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        onProgressChange((offset.x / w).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        onProgressChange((change.position.x / w).coerceIn(0f, 1f))
                    }
                },
    ) {
        val stroke = trackHeight.toPx()
        val y = size.height / 2f
        val thumbR = thumbRadius.toPx()
        val w = size.width
        val inset = thumbR
        val trackW = (w - inset * 2).coerceAtLeast(0f)
        val startX = inset
        val endX = startX + trackW
        val activeX = startX + trackW * p

        // Inactive
        drawLine(
            color = look.inactiveTrack,
            start = Offset(startX, y),
            end = Offset(endX, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Active (gradient or solid)
        if (p > 0f && trackW > 0f) {
            drawLine(
                brush = look.seekBrush(),
                start = Offset(startX, y),
                end = Offset(activeX, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        // Thumb
        drawCircle(
            color = look.accent,
            radius = thumbR,
            center = Offset(activeX, y),
        )
        drawCircle(
            color = look.onAccent.copy(alpha = 0.15f),
            radius = thumbR * 0.45f,
            center = Offset(activeX, y),
        )
    }
}

@Composable
fun GradientSeekBarMs(
    positionMs: Long,
    durationMs: Long,
    onSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    GradientSeekBar(
        progress = progress,
        onProgressChange = { onSeekMs((it * duration).roundToLong()) },
        modifier = modifier,
    )
}
