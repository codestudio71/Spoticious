package com.codestudio71.spoticious.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun SpoticiousTheme(
    lookId: SpoticiousLookId = SpoticiousLookId.MIAMI,
    content: @Composable () -> Unit,
) {
    val look = SpoticiousLooks.byId(lookId)
    val colorScheme =
        if (look.isLight) {
            lightColorScheme(
                primary = look.accent,
                secondary = look.accentAlt,
                tertiary = look.accent,
                background = look.gradientColors.first(),
                surface = look.panelFill,
                onPrimary = look.onAccent,
                onSecondary = look.onAccent,
                onTertiary = look.onAccent,
                onBackground = look.textPrimary,
                onSurface = look.textPrimary,
            )
        } else {
            darkColorScheme(
                primary = look.accent,
                secondary = look.accentAlt,
                tertiary = look.accentAlt,
                background = look.gradientColors.first(),
                surface = look.panelFill,
                onPrimary = look.onAccent,
                onSecondary = look.onAccent,
                onTertiary = look.onAccent,
                onBackground = look.textPrimary,
                onSurface = look.textPrimary,
            )
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? android.app.Activity ?: return@SideEffect
            @Suppress("DEPRECATION")
            activity.window.navigationBarColor = look.navigationBarArgb
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = look.isLight
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightNavigationBars = look.isLight
        }
    }

    CompositionLocalProvider(LocalSpoticiousLook provides look) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
