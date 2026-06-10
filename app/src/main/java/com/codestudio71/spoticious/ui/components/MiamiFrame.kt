package com.codestudio71.spoticious.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codestudio71.spoticious.ui.theme.MiamiCyan
import com.codestudio71.spoticious.ui.theme.MiamiPink

/** Ciemne tło aplikacji — półprzezroczyste, gradient ekranu prześwieca. */
private val MiamiFrameFill = Color(0xFF0D0D1A).copy(alpha = 0.8f)

private val MiamiFrameShape = RoundedCornerShape(16.dp)

/**
 * Miami Vice ramka: półprzezroczyste tło, cyan border (jak seekbar), zaokrąglenie 16.dp.
 */
@Composable
fun MiamiFrame(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderWidth = if (highlighted) 2.dp else 1.dp
    val borderColor =
        if (highlighted) {
            MiamiCyan
        } else {
            MiamiCyan.copy(alpha = 0.85f)
        }
    val elevation = if (highlighted) 8.dp else 4.dp
    val fill =
        if (highlighted) {
            MiamiFrameFill.copy(alpha = 0.92f)
        } else {
            MiamiFrameFill
        }

    Column(
        modifier =
            modifier
                .shadow(
                    elevation = elevation,
                    shape = MiamiFrameShape,
                    ambientColor =
                        if (highlighted) {
                            MiamiPink.copy(alpha = 0.22f)
                        } else {
                            MiamiCyan.copy(alpha = 0.12f)
                        },
                    spotColor =
                        if (highlighted) {
                            MiamiCyan.copy(alpha = 0.38f)
                        } else {
                            MiamiCyan.copy(alpha = 0.18f)
                        },
                )
                .clip(MiamiFrameShape)
                .border(width = borderWidth, color = borderColor, shape = MiamiFrameShape)
                .background(fill, MiamiFrameShape)
                .padding(contentPadding),
        content = content,
    )
}

/**
 * Lżejszy wariant dla długich list (np. utwory): subtelne tło + dolna linia cyan 1.dp.
 */
@Composable
fun MiamiListRowFrame(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0D1A).copy(alpha = 0.35f))
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawLine(
                        color = MiamiCyan.copy(alpha = 0.75f),
                        start = Offset(0f, size.height - stroke / 2f),
                        end = Offset(size.width, size.height - stroke / 2f),
                        strokeWidth = stroke,
                    )
                }
                .padding(contentPadding),
        content = content,
    )
}
