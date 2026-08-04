package com.codestudio71.spoticious.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import com.codestudio71.spoticious.ui.theme.SpoticiousLook
import com.codestudio71.spoticious.ui.theme.SpoticiousLookId
import com.codestudio71.spoticious.ui.theme.SpoticiousLooks
import com.codestudio71.spoticious.ui.theme.appVerticalGradient

@Composable
fun NewLookScreen(
    selectedLookId: SpoticiousLookId,
    onLookSelected: (SpoticiousLookId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chrome = LocalSpoticiousLook.current
    val titleColor =
        if (chrome.id == SpoticiousLookId.FUTURE_CRYPTO) chrome.accentAlt else chrome.accent

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(appVerticalGradient()),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = chrome.textPrimary,
                )
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text = stringResource(R.string.new_look_title),
                    color = titleColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.new_look_hint),
                    color = chrome.textMuted,
                    fontSize = 12.sp,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(SpoticiousLooks.all, key = { it.id.storageKey }) { look ->
                LookPreviewCard(
                    look = look,
                    selected = look.id == selectedLookId,
                    onClick = { onLookSelected(look.id) },
                )
            }
        }
    }
}

@Composable
private fun LookPreviewCard(
    look: SpoticiousLook,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val chrome = LocalSpoticiousLook.current
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (selected) chrome.accent else chrome.accent.copy(alpha = 0.35f)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, borderColor, shape)
                .background(chrome.panelFill, shape)
                .clickable(onClick = onClick)
                .padding(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(look.gradientColors))
                    .border(1.dp, look.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        Modifier
                            .size(width = 28.dp, height = 6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(look.accent),
                    )
                    Box(
                        Modifier
                            .size(width = 18.dp, height = 6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(look.accentAlt),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                repeat(4) { i ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (i == 1) {
                                        Brush.horizontalGradient(look.listSelectedColors)
                                    } else {
                                        Brush.horizontalGradient(
                                            listOf(
                                                look.textPrimary.copy(alpha = 0.12f),
                                                look.textPrimary.copy(alpha = 0.08f),
                                            ),
                                        )
                                    },
                                ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(look.playBrush()),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(look.inactiveTrack),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.55f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(look.seekBrush()),
                    )
                }
            }
            if (look.beta) {
                Text(
                    text = stringResource(R.string.look_beta_badge),
                    color = look.onAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(look.accentAlt.copy(alpha = 0.92f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(look.titleRes),
            color = chrome.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        Text(
            text = stringResource(look.subtitleRes),
            color = chrome.textMuted,
            fontSize = 11.sp,
            maxLines = 2,
        )
        if (selected) {
            Text(
                text = stringResource(R.string.look_active),
                color = chrome.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
