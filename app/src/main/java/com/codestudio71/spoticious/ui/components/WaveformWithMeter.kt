package com.codestudio71.spoticious.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun WaveformWithMeter(
    label: String,
    frames: List<FrameResult>,
    dbfs: Float,
    peakDbfs: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .height(80.dp)
                .fillMaxWidth()
                .background(color = WaveCardBg, shape = RoundedCornerShape(12.dp))
                .border(width = 1.dp, color = WaveBorderColor, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = WaveLabelColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
        ) {
            RealTimeWaveform(
                frames = frames,
                waveColor = WaveGradient,
                modifier =
                    Modifier
                        .weight(0.82f)
                        .fillMaxHeight(),
            )
            DbfsMeter(
                currentDbfs = dbfs,
                peakDbfs = peakDbfs,
                modifier =
                    Modifier
                        .weight(0.18f)
                        .fillMaxHeight(),
            )
        }
    }
}
