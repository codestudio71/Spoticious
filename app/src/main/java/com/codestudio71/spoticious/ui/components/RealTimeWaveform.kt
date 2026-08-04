package com.codestudio71.spoticious.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.codestudio71.spoticious.audio.record.FrameResult
import kotlin.math.max
import kotlin.math.min

private const val POINT_CAP = 220
private const val SMOOTHING = 0.35f

private fun appendCubicStrip(
    path: Path,
    xs: FloatArray,
    ys: FloatArray,
    n: Int,
) {
    if (n < 2) return
    if (n == 2) {
        path.lineTo(xs[1], ys[1])
        return
    }
    for (i in 0 until n - 1) {
        val p0x = xs[i]
        val p0y = ys[i]
        val p1x = xs[i + 1]
        val p1y = ys[i + 1]
        val prevX = if (i > 0) xs[i - 1] else p0x
        val prevY = if (i > 0) ys[i - 1] else p0y
        val nextX = if (i + 2 < n) xs[i + 2] else p1x
        val nextY = if (i + 2 < n) ys[i + 2] else p1y
        val c1x = p0x + SMOOTHING * (p1x - prevX)
        val c1y = p0y + SMOOTHING * (p1y - prevY)
        val c2x = p1x - SMOOTHING * (nextX - p0x)
        val c2y = p1y - SMOOTHING * (nextY - p0y)
        path.cubicTo(c1x, c1y, c2x, c2y, p1x, p1y)
    }
}

@Composable
fun RealTimeWaveform(
    frames: List<FrameResult>,
    modifier: Modifier = Modifier,
    waveColor: Brush,
) {
    val look = LocalSpoticiousLook.current
    val density = LocalDensity.current
    val zeroThicknessPx =
        remember(density) {
            with(density) { 0.5.dp.toPx() }
        }
    val envelopePath = remember { Path() }
    val xs = remember { FloatArray(POINT_CAP) }
    val ysTop = remember { FloatArray(POINT_CAP) }
    val ysBot = remember { FloatArray(POINT_CAP) }
    val rx = remember { FloatArray(POINT_CAP) }
    val ry = remember { FloatArray(POINT_CAP) }

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF1A1030)),
    ) {
        val w = size.width
        val h = size.height
        val cy = h * 0.5f
        val verticalPadPx = max(8f, h * 0.06f)
        val halfAmpl = (cy - verticalPadPx).coerceAtLeast(8f)

        val lineColor = look.textMuted.copy(alpha = 0.35f)
        drawLine(
            color = lineColor,
            start = Offset(0f, cy),
            end = Offset(w, cy),
            strokeWidth = zeroThicknessPx,
        )

        val n = frames.size
        val path = envelopePath
        path.reset()

        when {
            n == 0 -> return@Canvas
            n == 1 -> {
                val f = frames[0]
                val minEnv = min(0f, f.minAmplitude).coerceAtLeast(-1f)
                val maxEnv = max(0f, f.maxAmplitude).coerceAtMost(1f)
                val yT = cy - maxEnv * halfAmpl
                val yB = cy - minEnv * halfAmpl
                val xMid = w * 0.5f
                path.moveTo(0f, cy)
                path.lineTo(xMid, yT)
                path.lineTo(w, cy)
                path.lineTo(xMid, yB)
                path.close()
            }
            else -> {
                var count = min(n, POINT_CAP)
                for (i in 0 until count) {
                    val t = i / (count - 1f)
                    xs[i] = t * w
                    val f = frames[frames.size - count + i]
                    val minEnv = min(0f, f.minAmplitude).coerceAtLeast(-1f)
                    val maxEnv = max(0f, f.maxAmplitude).coerceAtMost(1f)
                    ysTop[i] = cy - maxEnv * halfAmpl
                    ysBot[i] = cy - minEnv * halfAmpl
                }
                path.moveTo(xs[0], ysTop[0])
                appendCubicStrip(path, xs, ysTop, count)
                path.lineTo(xs[count - 1], ysBot[count - 1])
                for (k in 0 until count) {
                    rx[k] = xs[count - 1 - k]
                    ry[k] = ysBot[count - 1 - k]
                }
                appendCubicStrip(path, rx, ry, count)
                path.lineTo(xs[0], ysTop[0])
                path.close()
            }
        }
        drawPath(
            path = path,
            brush = waveColor,
            style = Fill,
        )
    }
}
