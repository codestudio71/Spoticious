package com.codestudio71.spoticious.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.player.EqualizerAudioProcessor
import com.codestudio71.spoticious.player.PlayerViewModel
import com.codestudio71.spoticious.ui.theme.EqBackground
import com.codestudio71.spoticious.ui.theme.EqMiamiCyan
import com.codestudio71.spoticious.ui.theme.EqMiamiPink
import kotlin.OptIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val eqEnabled by viewModel.eqEnabled.collectAsState()
    val bandGains by viewModel.eqBandGains.collectAsState()
    val preampDb by viewModel.eqPreampDb.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF003344),
                        Color(0xFF2D0050)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                text = stringResource(R.string.eq),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (eqEnabled) stringResource(R.string.eq_on) else stringResource(R.string.eq_off),
                    color = if (eqEnabled) EqMiamiCyan else Color.Gray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = { viewModel.setEqEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EqBackground,
                        checkedTrackColor = EqMiamiCyan,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )
                }
            }

            Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.eq_pre),
                    color = EqMiamiPink,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(48.dp)
                )
                Slider(
                    value = preampDb,
                    onValueChange = { viewModel.setEqPreamp(it) },
                    valueRange = -15f..15f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF4DB8),
                        activeTrackColor = Color(0xFFFF4444),
                        inactiveTrackColor = Color(0xFF662222)
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .shadow(
                                    elevation = 4.dp,
                                    shape = CircleShape,
                                    ambientColor = Color(0xFFFF4DB8),
                                    spotColor = Color(0xFFFF4DB8)
                                )
                                .background(
                                    color = Color(0xFFFF4DB8),
                                    shape = CircleShape
                                )
                        )
                    }
                )
                Text(
                    text = formatDb(preampDb),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.width(52.dp)
                )
            }

            EqualizerAudioProcessor.BAND_FREQUENCIES_HZ.forEachIndexed { index, freqHz ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatFreq(freqHz),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        modifier = Modifier.width(48.dp)
                    )
                    Slider(
                        value = (bandGains.getOrElse(index) { 0f } + 15f) / 30f,
                        onValueChange = { viewModel.setEqBandGain(index, it * 30f - 15f) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF4DB8),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF333333)
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .shadow(
                                        elevation = 4.dp,
                                        shape = CircleShape,
                                        ambientColor = Color(0xFFFF4DB8),
                                        spotColor = Color(0xFFFF4DB8)
                                    )
                                    .background(
                                        color = Color(0xFFFF4DB8),
                                        shape = CircleShape
                                    )
                            )
                        }
                    )
                    Text(
                        text = formatDb(bandGains.getOrElse(index) { 0f }),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(52.dp)
                    )
                }
            }
        }

            TextButton(
                onClick = { viewModel.resetEq() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.eq_reset),
                    color = EqMiamiCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatDb(db: Float): String {
    return when {
        db > 0 -> "+${String.format("%.1f", db)}"
        db < 0 -> String.format("%.1f", db)
        else -> "0.0"
    }
}

private fun formatFreq(hz: Int): String {
    return when {
        hz >= 1000 -> "${hz / 1000}kHz"
        else -> "${hz}Hz"
    }
}
