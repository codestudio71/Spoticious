@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.codestudio71.spoticious.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.ui.components.MiamiFrame
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import com.codestudio71.spoticious.ui.theme.SpoticiousLookId
import com.codestudio71.spoticious.ui.theme.miamiVerticalGradient
import com.codestudio71.spoticious.ui.theme.usesPremiumExtraChrome

@Composable
fun ExtraScreen(
    onBack: () -> Unit,
    onPlaylistClick: () -> Unit,
    onRecordPreviewClick: () -> Unit,
    onWrappedClick: () -> Unit,
    onAudioCutClick: () -> Unit,
    onNewLookClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalSpoticiousLook.current
    if (look.usesPremiumExtraChrome) {
        PremiumExtraScreen(
            onBack = onBack,
            onPlaylistClick = onPlaylistClick,
            onRecordPreviewClick = onRecordPreviewClick,
            onWrappedClick = onWrappedClick,
            onAudioCutClick = onAudioCutClick,
            onNewLookClick = onNewLookClick,
            modifier = modifier,
        )
    } else {
        // Classic Miami (and other non-premium looks) — layout/chrome unchanged
        ClassicExtraScreen(
            onBack = onBack,
            onPlaylistClick = onPlaylistClick,
            onRecordPreviewClick = onRecordPreviewClick,
            onWrappedClick = onWrappedClick,
            onAudioCutClick = onAudioCutClick,
            onNewLookClick = onNewLookClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun ClassicExtraScreen(
    onBack: () -> Unit,
    onPlaylistClick: () -> Unit,
    onRecordPreviewClick: () -> Unit,
    onWrappedClick: () -> Unit,
    onAudioCutClick: () -> Unit,
    onNewLookClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val look = LocalSpoticiousLook.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(miamiVerticalGradient())
                .verticalScroll(rememberScrollState()),
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
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = look.textPrimary,
                )
            }
            Text(
                text = stringResource(R.string.extra_title),
                color = look.accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClassicExtraCard(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.new_look),
                subtitle = stringResource(R.string.new_look_subtitle),
                onClick = onNewLookClick,
            )
            ClassicExtraCard(
                icon = Icons.Default.BarChart,
                title = stringResource(R.string.wrapped),
                subtitle = stringResource(R.string.wrapped_subtitle),
                onClick = onWrappedClick,
            )
            ClassicExtraCard(
                icon = Icons.Default.Mic,
                title = stringResource(R.string.record_preview),
                subtitle = stringResource(R.string.record_preview_subtitle),
                onClick = onRecordPreviewClick,
            )
            ClassicExtraCard(
                icon = Icons.Default.PlaylistPlay,
                title = stringResource(R.string.playlist),
                subtitle = stringResource(R.string.playlist_queue),
                onClick = onPlaylistClick,
            )
            ClassicExtraCard(
                icon = Icons.Default.ContentCut,
                title = stringResource(R.string.audio_cut),
                subtitle = stringResource(R.string.audio_cut_subtitle),
                onClick = onAudioCutClick,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.8f)
                        .height(1.dp)
                        .background(look.textPrimary.copy(alpha = 0.2f)),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.about_app),
                color = look.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            ClassicExtraButton(
                icon = Icons.Default.Payment,
                text = stringResource(R.string.support_project),
                copyText = "https://www.paypal.com/donate/?hosted_button_id=H9DVM6NZ8TD6A",
                onClick = {
                    val intent =
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.paypal.com/donate/?hosted_button_id=H9DVM6NZ8TD6A"),
                        )
                    context.startActivity(intent)
                },
            )
            ClassicExtraButton(
                icon = Icons.Default.Email,
                text = stringResource(R.string.contact),
                copyText = "codestudio71@pm.me",
                onClick = {
                    val intent =
                        Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:codestudio71@pm.me")
                        }
                    context.startActivity(intent)
                },
            )
            ClassicExtraButton(
                icon = Icons.Default.Code,
                text = stringResource(R.string.github),
                copyText = "https://github.com/codestudio71",
                onClick = {
                    val intent =
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/codestudio71"))
                    context.startActivity(intent)
                },
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PremiumExtraScreen(
    onBack: () -> Unit,
    onPlaylistClick: () -> Unit,
    onRecordPreviewClick: () -> Unit,
    onWrappedClick: () -> Unit,
    onAudioCutClick: () -> Unit,
    onNewLookClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val look = LocalSpoticiousLook.current
    val isCrypto = look.id == SpoticiousLookId.FUTURE_CRYPTO
    val titleColor = if (isCrypto) look.accentAlt else look.accent
    val sectionColor = if (isCrypto) look.accentAlt else look.accent

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(miamiVerticalGradient()),
    ) {
        LookExtraAtmosphere(Modifier = Modifier.fillMaxSize())

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
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
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = look.textPrimary,
                    )
                }
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = stringResource(R.string.extra_title),
                        color = titleColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Box(
                        modifier =
                            Modifier
                                .padding(top = 4.dp)
                                .fillMaxWidth(0.35f)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(look.accent, look.accentAlt),
                                    ),
                                ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PremiumExtraCard(
                    icon = Icons.Default.Palette,
                    title = stringResource(R.string.new_look),
                    subtitle = stringResource(R.string.new_look_subtitle),
                    onClick = onNewLookClick,
                )
                PremiumExtraCard(
                    icon = Icons.Default.BarChart,
                    title = stringResource(R.string.wrapped),
                    subtitle = stringResource(R.string.wrapped_subtitle),
                    onClick = onWrappedClick,
                )
                PremiumExtraCard(
                    icon = Icons.Default.Mic,
                    title = stringResource(R.string.record_preview),
                    subtitle = stringResource(R.string.record_preview_subtitle),
                    onClick = onRecordPreviewClick,
                )
                PremiumExtraCard(
                    icon = Icons.Default.PlaylistPlay,
                    title = stringResource(R.string.playlist),
                    subtitle = stringResource(R.string.playlist_queue),
                    onClick = onPlaylistClick,
                )
                PremiumExtraCard(
                    icon = Icons.Default.ContentCut,
                    title = stringResource(R.string.audio_cut),
                    subtitle = stringResource(R.string.audio_cut_subtitle),
                    onClick = onAudioCutClick,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        look.accent.copy(alpha = 0.85f),
                                        look.accentAlt.copy(alpha = 0.85f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_app),
                    color = sectionColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                PremiumExtraButton(
                    icon = Icons.Default.Payment,
                    text = stringResource(R.string.support_project),
                    copyText = "https://www.paypal.com/donate/?hosted_button_id=H9DVM6NZ8TD6A",
                    onClick = {
                        val intent =
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.paypal.com/donate/?hosted_button_id=H9DVM6NZ8TD6A"),
                            )
                        context.startActivity(intent)
                    },
                )
                PremiumExtraButton(
                    icon = Icons.Default.Email,
                    text = stringResource(R.string.contact),
                    copyText = "codestudio71@pm.me",
                    onClick = {
                        val intent =
                            Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:codestudio71@pm.me")
                            }
                        context.startActivity(intent)
                    },
                )
                PremiumExtraButton(
                    icon = Icons.Default.Code,
                    text = stringResource(R.string.github),
                    copyText = "https://github.com/codestudio71",
                    onClick = {
                        val intent =
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/codestudio71"))
                        context.startActivity(intent)
                    },
                )
            }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ClassicExtraCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    val look = LocalSpoticiousLook.current
    MiamiFrame(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        contentPadding = 20.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = look.accentAlt,
                modifier = Modifier.padding(end = 16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = look.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    color = look.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun PremiumExtraCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    val look = LocalSpoticiousLook.current
    val isCrypto = look.id == SpoticiousLookId.FUTURE_CRYPTO
    MiamiFrame(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        contentPadding = 18.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PremiumIconBadge(icon = icon, crypto = isCrypto)
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
            ) {
                Text(
                    text = title,
                    color = look.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = look.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun PremiumIconBadge(
    icon: ImageVector,
    crypto: Boolean,
) {
    val look = LocalSpoticiousLook.current
    val shape = if (crypto) RoundedCornerShape(10.dp) else CircleShape
    val borderBrush =
        Brush.linearGradient(
            listOf(look.accent, look.accentAlt),
        )
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .then(
                    if (crypto) {
                        Modifier.shadow(
                            elevation = 10.dp,
                            shape = shape,
                            ambientColor = look.accentAlt.copy(alpha = 0.45f),
                            spotColor = look.accent.copy(alpha = 0.4f),
                        )
                    } else {
                        Modifier.shadow(
                            elevation = 6.dp,
                            shape = shape,
                            ambientColor = look.accent.copy(alpha = 0.25f),
                            spotColor = look.accentAlt.copy(alpha = 0.2f),
                        )
                    },
                )
                .clip(shape)
                .background(
                    if (crypto) {
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF1A0830).copy(alpha = 0.9f),
                                Color(0xFF080418).copy(alpha = 0.85f),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.14f),
                                look.panelFillHighlighted,
                            ),
                        )
                    },
                    shape,
                )
                .border(1.dp, borderBrush, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (crypto) look.accentAlt else look.accent,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ClassicExtraButton(
    icon: ImageVector,
    text: String,
    copyText: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val look = LocalSpoticiousLook.current
    MiamiFrame(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        val clipboard =
                            context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("link", copyText))
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.link_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                    },
                ),
        contentPadding = 16.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = look.accent,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text = text,
                color = look.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PremiumExtraButton(
    icon: ImageVector,
    text: String,
    copyText: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val look = LocalSpoticiousLook.current
    val isCrypto = look.id == SpoticiousLookId.FUTURE_CRYPTO
    MiamiFrame(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        val clipboard =
                            context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("link", copyText))
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.link_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                    },
                ),
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isCrypto) look.accentAlt else look.accent,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text = text,
                color = if (isCrypto) look.accentAlt else look.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
