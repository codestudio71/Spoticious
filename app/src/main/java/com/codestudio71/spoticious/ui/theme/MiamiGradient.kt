package com.codestudio71.spoticious.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Jedno źródło prawdy dla tła aplikacji (Miami): MainScreen + zakładki. */
val MiamiGradientColors =
    listOf(
        Color(0xFF0D0D1A),
        Color(0xFF1A0A2E),
        Color(0xFF2D1B4E),
    )

fun miamiVerticalGradient(): Brush = Brush.verticalGradient(MiamiGradientColors)

/** Zaokrąglone podświetlenie wiersza listy (Tracks / pliki w folderze) — bez ramki. */
val MiamiListRowShape = RoundedCornerShape(12.dp)

fun miamiListSelectedBrush(): Brush =
    Brush.verticalGradient(
        colors =
            listOf(
                MiamiGradientColors[1].copy(alpha = 0.72f),
                MiamiGradientColors[2].copy(alpha = 0.58f),
            ),
    )
