package com.codestudio71.spoticious.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Legacy constant — identical to [SpoticiousLooks.Miami] gradient (default skin). */
val MiamiGradientColors: List<Color>
    get() = SpoticiousLooks.Miami.gradientColors

@Composable
@ReadOnlyComposable
fun appVerticalGradient(): Brush = LocalSpoticiousLook.current.verticalGradient()

/** @deprecated Prefer [appVerticalGradient] — kept so call sites still compile; now look-aware. */
@Composable
@ReadOnlyComposable
fun miamiVerticalGradient(): Brush = appVerticalGradient()

val MiamiListRowShape = RoundedCornerShape(12.dp)

@Composable
@ReadOnlyComposable
fun appListSelectedBrush(): Brush = LocalSpoticiousLook.current.listSelectedBrush()

@Composable
@ReadOnlyComposable
fun miamiListSelectedBrush(): Brush = appListSelectedBrush()

/** Selected list row: glass fill + accent rim + left neon bar (premium looks). */
@Composable
fun Modifier.appListSelectedRow(selected: Boolean, shape: RoundedCornerShape = MiamiListRowShape): Modifier {
    if (!selected) return this
    val look = LocalSpoticiousLook.current
    val barBrush =
        Brush.verticalGradient(listOf(look.accent, look.accentAlt))
    var m =
        this
            .clip(shape)
            .background(look.listSelectedBrush(), shape)
            .drawWithContent {
                drawContent()
                if (look.listSelectedBorder.alpha > 0.01f) {
                    val w = 3.dp.toPx()
                    drawRect(
                        brush = barBrush,
                        topLeft = Offset.Zero,
                        size = Size(w, size.height),
                    )
                }
            }
    if (look.listSelectedBorder.alpha > 0.01f) {
        // Gradient rim only for Miami 2 / Future Crypto — classic Miami stays borderless
        m =
            when (look.id) {
                SpoticiousLookId.FUTURE_CRYPTO,
                SpoticiousLookId.MIAMI_2,
                ->
                    m.border(
                        width = 1.5.dp,
                        brush =
                            Brush.horizontalGradient(
                                listOf(look.accentAlt, look.accent),
                            ),
                        shape = shape,
                    )
                else -> m.border(1.dp, look.listSelectedBorder, shape)
            }
    }
    return m
}

val MiamiPanelFill: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSpoticiousLook.current.panelFill

val MiamiPanelFillHighlighted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSpoticiousLook.current.panelFillHighlighted

val MiamiDialogFill: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSpoticiousLook.current.dialogFill

val MiamiMenuShape = RoundedCornerShape(12.dp)

@Composable
fun Modifier.appMenuSurface(shape: RoundedCornerShape = MiamiMenuShape): Modifier {
    val look = LocalSpoticiousLook.current
    return when (look.id) {
        SpoticiousLookId.FUTURE_CRYPTO ->
            this
                .clip(shape)
                .background(look.menuFill, shape)
                .border(
                    width = 1.5.dp,
                    brush =
                        Brush.horizontalGradient(
                            listOf(look.accentAlt.copy(alpha = 0.95f), look.accent.copy(alpha = 0.95f)),
                        ),
                    shape = shape,
                )
        SpoticiousLookId.MIAMI_2 ->
            this
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            look.menuFill,
                        ),
                    ),
                    shape,
                )
                .border(
                    width = 1.dp,
                    brush =
                        Brush.horizontalGradient(
                            listOf(look.accent.copy(alpha = 0.8f), look.accentAlt.copy(alpha = 0.75f)),
                        ),
                    shape = shape,
                )
        else ->
            // Classic Miami menu chrome — unchanged
            this
                .clip(shape)
                .background(look.menuFill, shape)
                .border(1.dp, look.accent.copy(alpha = 0.85f), shape)
    }
}

@Composable
fun Modifier.miamiMenuSurface(shape: RoundedCornerShape = MiamiMenuShape): Modifier = appMenuSurface(shape)
