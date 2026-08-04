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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.audio.record.FrameResult
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook

@Composable
fun WaveformWithMeter(
    label: String,
    frames: List<FrameResult>,
    dbfs: Float,
    peakDbfs: Float,
    modifier: Modifier = Modifier,
) {
    val look = LocalSpoticiousLook.current
    val waveGradient = Brush.verticalGradient(listOf(look.accent, look.accentAlt))
    Column(
        modifier =
            modifier
                .height(80.dp)
                .fillMaxWidth()
                .background(color = look.panelFill, shape = RoundedCornerShape(12.dp))
                .border(width = 1.dp, color = look.accent.copy(alpha = 0.35f), shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = look.accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier.height(4.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
        ) {
            RealTimeWaveform(
                frames = frames,
                waveColor = waveGradient,
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
