package com.codestudio71.spoticious.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.audio.cut.WaveformPeaks
import com.codestudio71.spoticious.audio.record.FrameResult
private val WaveGradient =
    Brush.verticalGradient(
        colors =
            listOf(
                Color(0xFF00E5FF),
                Color(0xFFFF006E),
            ),
    )

private val WaveCardBg = Color(0xFF1A1030)
private val WaveBorderColor = Color(0xFF00BCD4).copy(alpha = 0.3f)
private val WaveLabelColor = Color(0xFF00E5FF)
private val PinkMarker = Color(0xFFFF006E)
private val CyanMarker = Color(0xFF00E5FF)

@Composable
fun StaticWaveform(
    peaks: WaveformPeaks,
    positionMs: Long,
    fromMs: Long,
    toMs: Long,
    onScrub: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = peaks.durationMs.coerceAtLeast(1L)
    val frames = remember(peaks) { peaksToFrames(peaks) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = WaveCardBg, shape = RoundedCornerShape(12.dp))
                .border(width = 1.dp, color = WaveBorderColor, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.audio_cut_waveform),
            color = WaveLabelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .pointerInput(durationMs) {
                        detectTapGestures { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onScrub((fraction * durationMs).toLong())
                        }
                    }
                    .pointerInput(durationMs) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onScrub((fraction * durationMs).toLong())
                        }
                    },
        ) {
            RealTimeWaveform(
                frames = frames,
                waveColor = WaveGradient,
                modifier = Modifier.fillMaxSize(),
            )
            WaveformMarkersOverlay(
                durationMs = durationMs,
                positionMs = positionMs,
                fromMs = fromMs,
                toMs = toMs,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = "${formatStepperTime(0)} — ${formatStepperTime(durationMs)}",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
    }
}

@Composable
private fun WaveformMarkersOverlay(
    durationMs: Long,
    positionMs: Long,
    fromMs: Long,
    toMs: Long,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val fromX = (fromMs.toFloat() / durationMs) * w
        val toX = (toMs.toFloat() / durationMs) * w
        val posX = (positionMs.toFloat() / durationMs) * w

        drawRect(
            color = CyanMarker.copy(alpha = 0.14f),
            topLeft = Offset(fromX.coerceIn(0f, w), 0f),
            size =
                androidx.compose.ui.geometry.Size(
                    (toX - fromX).coerceAtLeast(0f),
                    h,
                ),
        )
        drawLine(
            color = Color.White.copy(alpha = 0.85f),
            start = Offset(posX, 0f),
            end = Offset(posX, h),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = CyanMarker,
            start = Offset(fromX, 0f),
            end = Offset(fromX, h),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = PinkMarker,
            start = Offset(toX, 0f),
            end = Offset(toX, h),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

private fun peaksToFrames(peaks: WaveformPeaks): List<FrameResult> {
    val count = peaks.min.size.coerceAtLeast(1)
    return List(count) { i ->
        val minA = peaks.min[i].coerceIn(-1f, 0f)
        val maxA = peaks.max[i].coerceIn(0f, 1f)
        val rms = ((maxA - minA) / 2f).coerceIn(0f, 1f)
        FrameResult(
            minAmplitude = minA,
            maxAmplitude = maxA,
            rms = rms,
            dbfs = -24f,
        )
    }
}
