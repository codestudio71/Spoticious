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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codestudio71.spoticious.ui.theme.LocalSpoticiousLook
import com.codestudio71.spoticious.ui.theme.SpoticiousLookId

private val MiamiFrameShape = RoundedCornerShape(16.dp)
private val Miami2GlassShape = RoundedCornerShape(20.dp)
private val CryptoNeonShape = RoundedCornerShape(14.dp)

/**
 * Shared panel chrome. Classic Miami / Sun / Spotify / Clear keep the original solid frame.
 * Only [SpoticiousLookId.MIAMI_2] and [SpoticiousLookId.FUTURE_CRYPTO] branch to upgraded treatments.
 */
@Composable
fun MiamiFrame(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    highlighted: Boolean = false,
    solidFill: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val look = LocalSpoticiousLook.current
    when (look.id) {
        SpoticiousLookId.FUTURE_CRYPTO ->
            CryptoNeonFrame(
                modifier = modifier,
                contentPadding = contentPadding,
                highlighted = highlighted,
                solidFill = solidFill,
                accent = look.accent,
                accentAlt = look.accentAlt,
                fill = if (highlighted) look.panelFillHighlighted else look.panelFill,
                content = content,
            )
        SpoticiousLookId.MIAMI_2 ->
            Miami2GlassFrame(
                modifier = modifier,
                contentPadding = contentPadding,
                highlighted = highlighted,
                solidFill = solidFill,
                accent = look.accent,
                accentAlt = look.accentAlt,
                fill = if (highlighted) look.panelFillHighlighted else look.panelFill,
                content = content,
            )
        else ->
            ClassicMiamiFrame(
                modifier = modifier,
                contentPadding = contentPadding,
                solidFill = solidFill,
                borderColor =
                    if (highlighted) {
                        look.accent
                    } else {
                        look.accent.copy(alpha = 0.85f)
                    },
                fill = if (highlighted) look.panelFillHighlighted else look.panelFill,
                ambientGlow =
                    if (highlighted) {
                        look.accentAlt.copy(alpha = 0.22f)
                    } else {
                        look.accent.copy(alpha = 0.12f)
                    },
                spotGlow =
                    if (highlighted) {
                        look.accent.copy(alpha = 0.38f)
                    } else {
                        look.accent.copy(alpha = 0.18f)
                    },
                elevation = if (highlighted) 8.dp else 4.dp,
                borderWidth = if (highlighted) 2.dp else 1.dp,
                content = content,
            )
    }
}

/** Pixel-stable classic frame path — do not alter for look experiments. */
@Composable
private fun ClassicMiamiFrame(
    modifier: Modifier,
    contentPadding: Dp,
    solidFill: Boolean,
    borderColor: Color,
    fill: Color,
    ambientGlow: Color,
    spotGlow: Color,
    elevation: Dp,
    borderWidth: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val frameModifier =
        if (solidFill) {
            modifier
                .shadow(
                    elevation = elevation,
                    shape = MiamiFrameShape,
                    ambientColor = ambientGlow,
                    spotColor = spotGlow,
                )
                .clip(MiamiFrameShape)
                .background(fill, MiamiFrameShape)
                .border(width = borderWidth, color = borderColor, shape = MiamiFrameShape)
        } else {
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
                            Size(
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

/** Miami 2.0 — frosted glass + dual-tone rim + soft sheen (not solid Miami card). */
@Composable
private fun Miami2GlassFrame(
    modifier: Modifier,
    contentPadding: Dp,
    highlighted: Boolean,
    solidFill: Boolean,
    accent: Color,
    accentAlt: Color,
    fill: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = Miami2GlassShape
    val borderBrush =
        Brush.horizontalGradient(
            listOf(
                accent.copy(alpha = if (highlighted) 0.95f else 0.7f),
                Color.White.copy(alpha = 0.35f),
                accentAlt.copy(alpha = if (highlighted) 0.95f else 0.7f),
            ),
        )
    val glassFill =
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (highlighted) 0.14f else 0.09f),
                fill,
                fill.copy(alpha = (fill.alpha * 0.92f).coerceIn(0f, 1f)),
            ),
        )
    val borderW = if (highlighted) 1.5.dp else 1.dp

    val frameModifier =
        if (solidFill) {
            modifier
                .shadow(
                    elevation = if (highlighted) 14.dp else 8.dp,
                    shape = shape,
                    ambientColor = accent.copy(alpha = 0.2f),
                    spotColor = accentAlt.copy(alpha = 0.18f),
                )
                .clip(shape)
                .background(glassFill, shape)
                .border(width = borderW, brush = borderBrush, shape = shape)
                .drawWithContent {
                    drawContent()
                    // Left glass accent bar
                    drawRect(
                        brush = Brush.verticalGradient(listOf(accent, accentAlt)),
                        topLeft = Offset.Zero,
                        size = Size(3.dp.toPx(), size.height),
                    )
                    // Top inner highlight
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                            ),
                        size = Size(size.width, size.height * 0.35f),
                    )
                }
        } else {
            modifier
                .clip(shape)
                .border(width = borderW, brush = borderBrush, shape = shape)
        }

    Column(
        modifier = frameModifier.padding(contentPadding),
        content = content,
    )
}

/** Future Crypto — neon magenta↔cyan gradient rim + outer glow bloom. */
@Composable
private fun CryptoNeonFrame(
    modifier: Modifier,
    contentPadding: Dp,
    highlighted: Boolean,
    solidFill: Boolean,
    accent: Color,
    accentAlt: Color,
    fill: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = CryptoNeonShape
    val borderBrush =
        Brush.horizontalGradient(
            listOf(
                accentAlt,
                accent,
                accentAlt.copy(alpha = 0.85f),
            ),
        )
    val innerFill =
        Brush.verticalGradient(
            listOf(
                Color(0xFF1A0830).copy(alpha = if (highlighted) 0.85f else 0.65f),
                fill,
                Color(0xFF080418).copy(alpha = 0.8f),
            ),
        )
    val borderW = if (highlighted) 2.dp else 1.5.dp

    val frameModifier =
        if (solidFill) {
            modifier
                .shadow(
                    elevation = if (highlighted) 20.dp else 12.dp,
                    shape = shape,
                    ambientColor = accentAlt.copy(alpha = 0.45f),
                    spotColor = accent.copy(alpha = 0.4f),
                )
                .drawBehind {
                    // Soft outer neon bloom
                    val pad = 6.dp.toPx()
                    drawRoundRect(
                        brush =
                            Brush.horizontalGradient(
                                listOf(
                                    accentAlt.copy(alpha = 0.22f),
                                    accent.copy(alpha = 0.18f),
                                    accentAlt.copy(alpha = 0.2f),
                                ),
                            ),
                        topLeft = Offset(-pad * 0.3f, -pad * 0.3f),
                        size = Size(size.width + pad * 0.6f, size.height + pad * 0.6f),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                }
                .clip(shape)
                .background(innerFill, shape)
                .border(width = borderW, brush = borderBrush, shape = shape)
                .drawWithContent {
                    drawContent()
                    // Inner neon hairline
                    val inset = 3.dp.toPx()
                    drawRoundRect(
                        brush =
                            Brush.horizontalGradient(
                                listOf(
                                    accent.copy(alpha = 0.35f),
                                    accentAlt.copy(alpha = 0.25f),
                                ),
                            ),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius(11.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
        } else {
            modifier
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    ambientColor = accentAlt.copy(alpha = 0.35f),
                    spotColor = accent.copy(alpha = 0.3f),
                )
                .clip(shape)
                .border(width = borderW, brush = borderBrush, shape = shape)
        }

    Column(
        modifier = frameModifier.padding(contentPadding),
        content = content,
    )
}

@Composable
fun MiamiListRowFrame(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val look = LocalSpoticiousLook.current
    when (look.id) {
        SpoticiousLookId.FUTURE_CRYPTO -> {
            val underline =
                Brush.horizontalGradient(
                    listOf(look.accentAlt.copy(alpha = 0.85f), look.accent.copy(alpha = 0.85f)),
                )
            val rowFill = look.panelFill.copy(alpha = 0.4f)
            Box(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .background(rowFill)
                        .drawBehind {
                            val stroke = 1.5.dp.toPx()
                            drawLine(
                                brush = underline,
                                start = Offset(0f, size.height - stroke / 2f),
                                end = Offset(size.width, size.height - stroke / 2f),
                                strokeWidth = stroke,
                            )
                        }
                        .padding(contentPadding),
                content = content,
            )
        }
        SpoticiousLookId.MIAMI_2 -> {
            val underline = look.accent.copy(alpha = 0.55f)
            val rowFill =
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.06f),
                        look.panelFill.copy(alpha = 0.35f),
                    ),
                )
            Box(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .background(rowFill)
                        .drawBehind {
                            val stroke = 1.dp.toPx()
                            drawLine(
                                color = underline,
                                start = Offset(0f, size.height - stroke / 2f),
                                end = Offset(size.width, size.height - stroke / 2f),
                                strokeWidth = stroke,
                            )
                        }
                        .padding(contentPadding),
                content = content,
            )
        }
        else -> {
            // Classic Miami list row — unchanged
            val underline = look.accent.copy(alpha = 0.75f)
            val rowFill = look.panelFill.copy(alpha = 0.35f)
            Box(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .background(rowFill)
                        .drawBehind {
                            val stroke = 1.dp.toPx()
                            drawLine(
                                color = underline,
                                start = Offset(0f, size.height - stroke / 2f),
                                end = Offset(size.width, size.height - stroke / 2f),
                                strokeWidth = stroke,
                            )
                        }
                        .padding(contentPadding),
                content = content,
            )
        }
    }
}
