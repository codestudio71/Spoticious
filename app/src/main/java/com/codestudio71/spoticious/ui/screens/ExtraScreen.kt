package com.codestudio71.spoticious.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.ui.theme.MiamiCyan
import com.codestudio71.spoticious.ui.theme.MiamiPink

@Composable
fun ExtraScreen(
    onBack: () -> Unit,
    onPlaylistClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D1117),
                        Color(0xFF1A1F26),
                        Color(0xFF151A22)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White
                )
            }
            Text(
                text = stringResource(R.string.extra_title),
                color = MiamiCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExtraCard(
                icon = Icons.Default.BarChart,
                title = stringResource(R.string.wrapped),
                subtitle = stringResource(R.string.coming_soon)
            )
            ExtraCard(
                icon = Icons.Default.Mic,
                title = stringResource(R.string.record_preview),
                subtitle = stringResource(R.string.coming_soon)
            )
            ExtraCard(
                icon = Icons.Default.PlaylistPlay,
                title = stringResource(R.string.playlist),
                subtitle = stringResource(R.string.playlist_queue),
                onClick = onPlaylistClick
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.about_app),
                color = MiamiCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            ExtraButton(
                icon = Icons.Default.Payment,
                text = stringResource(R.string.support_project),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/donate/?hosted_button_id=H9DVM6NZ8TD6A"))
                    context.startActivity(intent)
                }
            )
            ExtraButton(
                icon = Icons.Default.Email,
                text = stringResource(R.string.contact),
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:codestudio71@pm.me")
                    }
                    context.startActivity(intent)
                }
            )
            ExtraButton(
                icon = Icons.Default.Code,
                text = stringResource(R.string.github),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/codestudio71"))
                    context.startActivity(intent)
                }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ExtraCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .background(Color(0xFF1A1F26), RoundedCornerShape(12.dp))
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiamiPink,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ExtraButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(MiamiCyan.copy(alpha = 0.3f), MiamiPink.copy(alpha = 0.3f))
                ),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiamiCyan,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = text,
            color = MiamiCyan,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
