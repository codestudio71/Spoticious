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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.audio.cut.WaveformPeaks
import com.codestudio71.spoticious.audio.record.FrameResult
import kotlin.math.abs

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

private enum class CutHandle {
    From,
    To,
}

@Composable
fun StaticWaveform(
    peaks: WaveformPeaks,
    fromMs: Long,
    toMs: Long,
    onFromChange: (Long) -> Unit,
    onToChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Playhead tylko podczas podglądu Od–Do; null = ukryty. */
    playheadMs: Long? = null,
) {
    val durationMs = peaks.durationMs.coerceAtLeast(1L)
    val frames = remember(peaks) { peaksToFrames(peaks) }
    val density = LocalDensity.current
    val hitSlopPx = with(density) { 28.dp.toPx() }
    val fromMsState = rememberUpdatedState(fromMs)
    val toMsState = rememberUpdatedState(toMs)
    val onFromState = rememberUpdatedState(onFromChange)
    val onToState = rememberUpdatedState(onToChange)

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
        Spacer(modifier.height(4.dp))

        var activeHandle by remember { mutableStateOf<CutHandle?>(null) }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .pointerInput(durationMs, hitSlopPx) {
                        fun xToMs(x: Float): Long {
                            val fraction = (x / size.width).coerceIn(0f, 1f)
                            return (fraction * durationMs).toLong()
                        }

                        fun pickHandle(x: Float): CutHandle {
                            val fromX = (fromMsState.value.toFloat() / durationMs) * size.width
                            val toX = (toMsState.value.toFloat() / durationMs) * size.width
                            val dFrom = abs(x - fromX)
                            val dTo = abs(x - toX)
                            return when {
                                dFrom <= hitSlopPx && dFrom <= dTo -> CutHandle.From
                                dTo <= hitSlopPx && dTo < dFrom -> CutHandle.To
                                dFrom <= dTo -> CutHandle.From
                                else -> CutHandle.To
                            }
                        }

                        detectTapGestures { offset ->
                            when (pickHandle(offset.x)) {
                                CutHandle.From -> onFromState.value(xToMs(offset.x))
                                CutHandle.To -> onToState.value(xToMs(offset.x))
                            }
                        }
                    }
                    .pointerInput(durationMs, hitSlopPx) {
                        fun xToMs(x: Float): Long {
                            val fraction = (x / size.width).coerceIn(0f, 1f)
                            return (fraction * durationMs).toLong()
                        }

                        fun pickHandle(x: Float): CutHandle {
                            val fromX = (fromMsState.value.toFloat() / durationMs) * size.width
                            val toX = (toMsState.value.toFloat() / durationMs) * size.width
                            val dFrom = abs(x - fromX)
                            val dTo = abs(x - toX)
                            return when {
                                dFrom <= hitSlopPx && dFrom <= dTo -> CutHandle.From
                                dTo <= hitSlopPx && dTo < dFrom -> CutHandle.To
                                dFrom <= dTo -> CutHandle.From
                                else -> CutHandle.To
                            }
                        }

                        detectDragGestures(
                            onDragStart = { offset ->
                                activeHandle = pickHandle(offset.x)
                            },
                            onDragEnd = { activeHandle = null },
                            onDragCancel = { activeHandle = null },
                            onDrag = { change, _ ->
                                change.consume()
                                val ms = xToMs(change.position.x)
                                when (activeHandle) {
                                    CutHandle.From -> onFromState.value(ms)
                                    CutHandle.To -> onToState.value(ms)
                                    null -> Unit
                                }
                            },
                        )
                    },
        ) {
            RealTimeWaveform(
                frames = frames,
                waveColor = WaveGradient,
                modifier = Modifier.fillMaxSize(),
            )
            WaveformMarkersOverlay(
                durationMs = durationMs,
                fromMs = fromMs,
                toMs = toMs,
                playheadMs = playheadMs,
                activeHandle = activeHandle,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = stringResource(R.string.audio_cut_waveform_hint),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
        Text(
            text = "${formatStepperTime(0)} — ${formatStepperTime(durationMs)}",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp),
        )
    }
}

@Composable
private fun WaveformMarkersOverlay(
    durationMs: Long,
    fromMs: Long,
    toMs: Long,
    playheadMs: Long?,
    activeHandle: CutHandle?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val fromX = (fromMs.toFloat() / durationMs) * w
        val toX = (toMs.toFloat() / durationMs) * w
        val handleW = 10.dp.toPx()
        val handleH = 12.dp.toPx()

        drawRect(
            color = CyanMarker.copy(alpha = 0.14f),
            topLeft = Offset(fromX.coerceIn(0f, w), 0f),
            size =
                androidx.compose.ui.geometry.Size(
                    (toX - fromX).coerceAtLeast(0f),
                    h,
                ),
        )

        fun drawHandle(
            x: Float,
            color: Color,
            emphasized: Boolean,
        ) {
            val stroke = if (emphasized) 3.dp.toPx() else 2.dp.toPx()
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = stroke,
            )
            val path =
                Path().apply {
                    moveTo(x, 0f)
                    lineTo(x - handleW / 2f, handleH)
                    lineTo(x + handleW / 2f, handleH)
                    close()
                }
            drawPath(path, color)
        }

        drawHandle(fromX, CyanMarker, activeHandle == CutHandle.From)
        drawHandle(toX, PinkMarker, activeHandle == CutHandle.To)

        val head = playheadMs
        if (head != null && durationMs > 0L) {
            val posX = (head.toFloat() / durationMs) * w
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(posX.coerceIn(0f, w), 0f),
                end = Offset(posX.coerceIn(0f, w), h),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
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
