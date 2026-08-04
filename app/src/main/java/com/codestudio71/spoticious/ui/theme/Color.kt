package com.codestudio71.spoticious.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Neon vibe – ciemne tło
val NeonBackground = Color(0xFF0A0E14)
val NeonSurface = Color(0xFF121922)

// Akcenty neonowe (turkus, fiolet, zieleń)
val NeonCyan = Color(0xFF00F5D4)
val NeonMagenta = Color(0xFF9B5DE5)
val NeonGreen = Color(0xFF00F5A0)

/** Static Miami defaults (identical to [SpoticiousLooks.Miami]). Prefer [SpoticiousThemeColors] in UI. */
val MiamiCyanStatic = Color(0xFF00D4FF)
val MiamiPinkStatic = Color(0xFFFF6B9D)

/**
 * Look-aware accent (was hardcoded Miami cyan).
 * In @Composable: resolves to current skin. Outside composition: Miami default.
 */
val MiamiCyan: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSpoticiousLook.current.accent

val MiamiPink: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSpoticiousLook.current.accentAlt

// EQ screen – Audacious-style (exact hex from spec); mapped to look when in composition via Eq accents below.
val EqBackground = Color(0xFF0D0D0D)
val EqMiamiCyanStatic = Color(0xFF00F5FF)
val EqMiamiPinkStatic = Color(0xFFFF006E)

val EqMiamiCyan: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSpoticiousLook.current.accent

val EqMiamiPink: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalSpoticiousLook.current.accentAlt

val Purple80 = Color(0xFF00F5D4)
val PurpleGrey80 = Color(0xFF8B9DC3)
val Pink80 = Color(0xFF9B5DE5)

val Purple40 = Color(0xFF00F5D4)
val PurpleGrey40 = Color(0xFF5A6B8A)
val Pink40 = Color(0xFF7B4BB5)
