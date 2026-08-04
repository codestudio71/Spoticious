package com.codestudio71.spoticious.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.codestudio71.spoticious.R

/**
 * Skin / look IDs. [MIAMI] is the shipping default — pixel-identical to Spoticious 2.0 chrome.
 * Other looks are beta for phone QA.
 */
enum class SpoticiousLookId(val storageKey: String) {
    MIAMI("miami"),
    MIAMI_2("miami2"),
    SPOTIFY("spotify"),
    FUTURE_CRYPTO("future_crypto"),
    SUN("sun"),
    CLEAR_SIMPLE("clear_simple"),
    ;

    companion object {
        fun fromStorageKey(key: String?): SpoticiousLookId {
            if (key.isNullOrBlank()) return MIAMI
            return entries.firstOrNull { it.storageKey == key } ?: MIAMI
        }
    }
}

@Immutable
data class SpoticiousLook(
    val id: SpoticiousLookId,
    /** Short label on picker tiles. */
    val titleRes: Int,
    val subtitleRes: Int,
    val beta: Boolean,
    /** Vertical screen gradient stops (top → bottom). */
    val gradientColors: List<Color>,
    val accent: Color,
    val accentAlt: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val panelFill: Color,
    val panelFillHighlighted: Color,
    val dialogFill: Color,
    val menuFill: Color,
    val listSelectedColors: List<Color>,
    /** Thin border on selected track row; [Color.Transparent] = classic Miami (no border). */
    val listSelectedBorder: Color,
    val inactiveTrack: Color,
    val playButtonColors: List<Color>,
    /** Active seek track (1 = solid, 2+ = gradient). */
    val seekActiveColors: List<Color>,
    /** Android window navigation bar (ARGB int). */
    val navigationBarArgb: Int,
    val isLight: Boolean,
) {
    fun verticalGradient(): Brush = Brush.verticalGradient(gradientColors)

    fun listSelectedBrush(): Brush =
        if (listSelectedBorder.alpha > 0.01f && listSelectedColors.size >= 2) {
            // Premium looks: horizontal glass wash (cyan→violet feel)
            Brush.horizontalGradient(listSelectedColors)
        } else {
            Brush.verticalGradient(listSelectedColors)
        }

    fun playBrush(): Brush =
        if (playButtonColors.size >= 2) {
            Brush.horizontalGradient(playButtonColors)
        } else {
            Brush.horizontalGradient(listOf(playButtonColors.first(), playButtonColors.first()))
        }

    fun seekBrush(): Brush =
        if (seekActiveColors.size >= 2) {
            Brush.horizontalGradient(seekActiveColors)
        } else {
            Brush.horizontalGradient(listOf(seekActiveColors.first(), seekActiveColors.first()))
        }
}

object SpoticiousLooks {
    /** Exact 2.0 Miami — do not drift. */
    val Miami =
        SpoticiousLook(
            id = SpoticiousLookId.MIAMI,
            titleRes = R.string.look_miami_title,
            subtitleRes = R.string.look_miami_subtitle,
            beta = false,
            gradientColors =
                listOf(
                    Color(0xFF0D0D1A),
                    Color(0xFF1A0A2E),
                    Color(0xFF2D1B4E),
                ),
            accent = Color(0xFF00D4FF),
            accentAlt = Color(0xFFFF6B9D),
            onAccent = Color(0xFF0D0D1A),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.85f),
            textMuted = Color.White.copy(alpha = 0.55f),
            panelFill = Color(0xFF0D0D1A).copy(alpha = 0.82f),
            panelFillHighlighted = Color(0xFF0D0D1A).copy(alpha = 0.92f),
            dialogFill = Color(0xFF0D0D1A).copy(alpha = 0.92f),
            menuFill = Color(0xFF0D0D1A),
            listSelectedColors =
                listOf(
                    Color(0xFF1A0A2E).copy(alpha = 0.72f),
                    Color(0xFF2D1B4E).copy(alpha = 0.58f),
                ),
            listSelectedBorder = Color.Transparent,
            inactiveTrack = Color.White.copy(alpha = 0.2f),
            playButtonColors = listOf(Color(0xFFFF6B9D), Color(0xFFFF6B9D)),
            seekActiveColors = listOf(Color(0xFF00D4FF)),
            navigationBarArgb = 0xFF0D0D1A.toInt(),
            isLight = false,
        )

    val Miami2 =
        SpoticiousLook(
            id = SpoticiousLookId.MIAMI_2,
            titleRes = R.string.look_miami2_title,
            subtitleRes = R.string.look_miami2_subtitle,
            beta = true,
            // Upgraded coastal glass — cooler teal night, not classic Miami purple stack
            gradientColors =
                listOf(
                    Color(0xFF040A14),
                    Color(0xFF0A1A30),
                    Color(0xFF1A1238),
                    Color(0xFF120A24),
                ),
            accent = Color(0xFF5CE1FF),
            accentAlt = Color(0xFFFF4FA0),
            onAccent = Color(0xFF061018),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.9f),
            textMuted = Color.White.copy(alpha = 0.5f),
            // Frosted glass panels (Extra frames paint these translucent)
            panelFill = Color(0xFF0C1A2E).copy(alpha = 0.42f),
            panelFillHighlighted = Color(0xFF142840).copy(alpha = 0.62f),
            dialogFill = Color(0xFF0E1C30).copy(alpha = 0.94f),
            menuFill = Color(0xFF0A1628).copy(alpha = 0.96f),
            listSelectedColors =
                listOf(
                    Color(0xFF1A3A55).copy(alpha = 0.85f),
                    Color(0xFF3A1848).copy(alpha = 0.7f),
                ),
            listSelectedBorder = Color(0xFF5CE1FF).copy(alpha = 0.75f),
            inactiveTrack = Color.White.copy(alpha = 0.14f),
            playButtonColors = listOf(Color(0xFF5CE1FF), Color(0xFFFF4FA0)),
            seekActiveColors = listOf(Color(0xFF5CE1FF), Color(0xFFFF4FA0)),
            navigationBarArgb = 0xFF040A14.toInt(),
            isLight = false,
        )

    val Spotify =
        SpoticiousLook(
            id = SpoticiousLookId.SPOTIFY,
            titleRes = R.string.look_spotify_title,
            subtitleRes = R.string.look_spotify_subtitle,
            beta = true,
            gradientColors =
                listOf(
                    Color(0xFF000000),
                    Color(0xFF0A0A0A),
                    Color(0xFF121212),
                ),
            accent = Color(0xFF1DB954),
            accentAlt = Color(0xFF1ED760),
            onAccent = Color(0xFF000000),
            textPrimary = Color.White,
            textSecondary = Color(0xFFB3B3B3),
            textMuted = Color(0xFF6A6A6A),
            panelFill = Color(0xFF181818).copy(alpha = 0.95f),
            panelFillHighlighted = Color(0xFF282828),
            dialogFill = Color(0xFF181818),
            menuFill = Color(0xFF181818),
            listSelectedColors =
                listOf(
                    Color(0xFF1DB954).copy(alpha = 0.22f),
                    Color(0xFF1DB954).copy(alpha = 0.12f),
                ),
            listSelectedBorder = Color(0xFF1DB954).copy(alpha = 0.45f),
            inactiveTrack = Color.White.copy(alpha = 0.18f),
            playButtonColors = listOf(Color(0xFF1DB954), Color(0xFF1ED760)),
            seekActiveColors = listOf(Color(0xFF1DB954)),
            navigationBarArgb = 0xFF000000.toInt(),
            isLight = false,
        )

    val FutureCrypto =
        SpoticiousLook(
            id = SpoticiousLookId.FUTURE_CRYPTO,
            titleRes = R.string.look_crypto_title,
            subtitleRes = R.string.look_crypto_subtitle,
            beta = true,
            // Purple-neo cyber — deep violet/black atmosphere (mockup vibe)
            gradientColors =
                listOf(
                    Color(0xFF020008),
                    Color(0xFF0A0020),
                    Color(0xFF1A0038),
                    Color(0xFF0C0228),
                ),
            accent = Color(0xFF00E5FF),
            accentAlt = Color(0xFFBD00FF),
            onAccent = Color(0xFF050010),
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.9f),
            textMuted = Color.White.copy(alpha = 0.48f),
            // Hollow neon glass — Extra frames add magenta→cyan glow borders
            panelFill = Color(0xFF12061F).copy(alpha = 0.5f),
            panelFillHighlighted = Color(0xFF1E0835).copy(alpha = 0.72f),
            dialogFill = Color(0xFF10061C).copy(alpha = 0.95f),
            menuFill = Color(0xFF0E0418),
            listSelectedColors =
                listOf(
                    Color(0xFF140828).copy(alpha = 0.9f),
                    Color(0xFF1A0A40).copy(alpha = 0.75f),
                ),
            listSelectedBorder = Color(0xFFBD00FF).copy(alpha = 0.9f),
            inactiveTrack = Color.White.copy(alpha = 0.14f),
            playButtonColors = listOf(Color(0xFF00E5FF), Color(0xFFBD00FF)),
            seekActiveColors = listOf(Color(0xFF00E5FF), Color(0xFFBD00FF)),
            navigationBarArgb = 0xFF020008.toInt(),
            isLight = false,
        )

    val Sun =
        SpoticiousLook(
            id = SpoticiousLookId.SUN,
            titleRes = R.string.look_sun_title,
            subtitleRes = R.string.look_sun_subtitle,
            beta = true,
            gradientColors =
                listOf(
                    Color(0xFFFFFBF2),
                    Color(0xFFE8F4FF),
                    Color(0xFFD6ECFF),
                ),
            accent = Color(0xFF2B8CFF),
            accentAlt = Color(0xFFFFC107),
            onAccent = Color(0xFF163A62),
            // Readable blue on light surfaces (not white)
            textPrimary = Color(0xFF163A62),
            textSecondary = Color(0xFF2B5580),
            textMuted = Color(0xFF5A7A9A),
            panelFill = Color.White.copy(alpha = 0.88f),
            panelFillHighlighted = Color(0xFFFFF3C4).copy(alpha = 0.95f),
            dialogFill = Color.White.copy(alpha = 0.96f),
            menuFill = Color.White,
            listSelectedColors =
                listOf(
                    Color(0xFFFFE082).copy(alpha = 0.75f),
                    Color(0xFFBBDEFB).copy(alpha = 0.65f),
                ),
            listSelectedBorder = Color.Transparent,
            inactiveTrack = Color(0xFF163A62).copy(alpha = 0.18f),
            playButtonColors = listOf(Color(0xFFFFC107), Color(0xFFFFD54F)),
            seekActiveColors = listOf(Color(0xFF2B8CFF), Color(0xFFFFC107)),
            navigationBarArgb = 0xFFD6ECFF.toInt(),
            isLight = true,
        )

    val ClearSimple =
        SpoticiousLook(
            id = SpoticiousLookId.CLEAR_SIMPLE,
            titleRes = R.string.look_clear_title,
            subtitleRes = R.string.look_clear_subtitle,
            beta = true,
            gradientColors =
                listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFF7F7F7),
                    Color(0xFFEEEEEE),
                ),
            accent = Color(0xFF111111),
            accentAlt = Color(0xFF333333),
            onAccent = Color.White,
            textPrimary = Color(0xFF111111),
            textSecondary = Color(0xFF333333),
            textMuted = Color(0xFF777777),
            panelFill = Color.White,
            panelFillHighlighted = Color(0xFFF0F0F0),
            dialogFill = Color.White,
            menuFill = Color.White,
            listSelectedColors =
                listOf(
                    Color(0xFFE8E8E8),
                    Color(0xFFE0E0E0),
                ),
            listSelectedBorder = Color.Transparent,
            inactiveTrack = Color(0xFF111111).copy(alpha = 0.12f),
            playButtonColors = listOf(Color(0xFF111111), Color(0xFF222222)),
            seekActiveColors = listOf(Color(0xFF111111)),
            navigationBarArgb = 0xFFFFFFFF.toInt(),
            isLight = true,
        )

    val all: List<SpoticiousLook> =
        listOf(Miami, Miami2, Spotify, FutureCrypto, Sun, ClearSimple)

    fun byId(id: SpoticiousLookId): SpoticiousLook =
        when (id) {
            SpoticiousLookId.MIAMI -> Miami
            SpoticiousLookId.MIAMI_2 -> Miami2
            SpoticiousLookId.SPOTIFY -> Spotify
            SpoticiousLookId.FUTURE_CRYPTO -> FutureCrypto
            SpoticiousLookId.SUN -> Sun
            SpoticiousLookId.CLEAR_SIMPLE -> ClearSimple
        }
}

val LocalSpoticiousLook =
    staticCompositionLocalOf { SpoticiousLooks.Miami }

/** Current look tokens — use in Compose UI instead of hardcoded MiamiCyan/Pink. */
object SpoticiousThemeColors {
    val current: SpoticiousLook
        @Composable
        @ReadOnlyComposable
        get() = LocalSpoticiousLook.current

    val accent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSpoticiousLook.current.accent

    val accentAlt: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSpoticiousLook.current.accentAlt

    val onAccent: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSpoticiousLook.current.onAccent

    val textPrimary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSpoticiousLook.current.textPrimary

    val textSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSpoticiousLook.current.textSecondary

    val textMuted: Color
        @Composable
        @ReadOnlyComposable
        get() = LocalSpoticiousLook.current.textMuted
}
