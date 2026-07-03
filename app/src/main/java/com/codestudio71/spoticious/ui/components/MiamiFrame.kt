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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codestudio71.spoticious.ui.theme.MiamiCyan
import com.codestudio71.spoticious.ui.theme.MiamiPanelFill
import com.codestudio71.spoticious.ui.theme.MiamiPanelFillHighlighted
import com.codestudio71.spoticious.ui.theme.MiamiPink

private val MiamiFrameShape = RoundedCornerShape(16.dp)

/**
 * Miami Vice ramka: półprzezroczyste tło, cyan border (jak seekbar), zaokrąglenie 16.dp.
 */
@Composable
fun MiamiFrame(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    highlighted: Boolean = false,
    /** Gdy false — bez szarego wypełnienia; gradient ekranu widać w środku ramki. */
    solidFill: Boolean = true,
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
    val fill = if (highlighted) MiamiPanelFillHighlighted else MiamiPanelFill

    val frameModifier =
        if (solidFill) {
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
                .background(fill, MiamiFrameShape)
                .border(width = borderWidth, color = borderColor, shape = MiamiFrameShape)
        } else {
            // Tylko obrys — bez cienia i bez wypełnienia; gradient MainScreen prześwieca 1:1.
            modifier
                .clip(MiamiFrameShape)
                .drawBehind {
                    val corner = MiamiFrameShape.topStart.toPx(size, this)
                    val stroke = borderWidth.toPx()
                    val inset = stroke / 2f
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(inset, inset),
                        size =
                            androidx.compose.ui.geometry.Size(
                                width = size.width - stroke,
                                height = size.height - stroke,
                            ),
                        cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
                        style = Stroke(width = stroke),
                    )
                }
        }

    Column(
        modifier = frameModifier.padding(contentPadding),
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
