package com.codestudio71.spoticious.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.codestudio71.spoticious.R
import kotlinx.coroutines.delay

/**
 * SplashScreen – szybki wjazd: logo na czerni, fade + bounce, ~0.9s → menu.
 * Pełna animacja v2: SplashScreenLegacy.kt
 */
@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    onTransitionComplete: () -> Unit
) {
    val logoVisible = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        logoVisible.value = true
        delay(900)
        onTransitionComplete()
    }

    val offsetY by animateFloatAsState(
        targetValue = if (logoVisible.value) 0f else 80f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible.value) 1f else 0f,
        animationSpec = tween(durationMillis = 400)
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (logoVisible.value) 1f else 0f,
        animationSpec = tween(durationMillis = 500)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .offset(y = offsetY.dp)
                .alpha(logoAlpha)
                .shadow(
                    elevation = (20 * glowAlpha).dp,
                    shape = CircleShape,
                    spotColor = Color(0xFF00E5FF),
                    ambientColor = Color(0xFFB040FF)
                )
        )
    }
}
