package com.codestudio71.spoticious.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codestudio71.spoticious.R
import com.codestudio71.spoticious.ui.theme.MiamiCyan
import com.codestudio71.spoticious.ui.theme.MiamiPink
import kotlinx.coroutines.delay

/**
 * SplashScreen v2 – pełna animacja (backup).
 * Użycie: w MainActivity podmienić SplashScreen na SplashScreenLegacy.
 *
 * Sekwencja: gradient cyan→pink (~2.5s) → błysk → fade do czerni → napis "Spoticious" + logo (~2s) → menu.
 */
@Composable
fun SplashScreenLegacy(
    modifier: Modifier = Modifier,
    onTransitionComplete: () -> Unit
) {
    var phase by remember { mutableStateOf(0) }
    val waveProgress = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }
    val blackFadeAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val logoVisible = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 1. Gradient cyan→pink BEZ napisu – ~2.55s
        waveProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(2550, easing = LinearEasing)
        )

        // 2. Błysk – miękka mgła
        phase = 1
        flashAlpha.animateTo(0.5f, animationSpec = tween(400, easing = LinearEasing))
        delay(200)
        flashAlpha.animateTo(0f, animationSpec = tween(600, easing = LinearEasing))
        delay(200)

        // 3. Przejście do czerni
        phase = 2
        blackFadeAlpha.animateTo(1f, animationSpec = tween(500, easing = LinearEasing))
        delay(100)

        // 4. Napis + logo
        textAlpha.animateTo(1f, animationSpec = tween(800, easing = LinearEasing))
        delay(200)
        logoVisible.value = true

        // 5. Napis zostaje 2 sekundy
        delay(2000)

        // 6. Przejście do menu
        phase = 3
        textAlpha.animateTo(0f, animationSpec = tween(800, easing = LinearEasing))
        delay(100)
        onTransitionComplete()
    }

    val offsetY by animateFloatAsState(
        targetValue = if (logoVisible.value) 0f else 120f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible.value) 1f else 0f,
        animationSpec = tween(durationMillis = 600)
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (logoVisible.value) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
    )

    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val centerY = heightPx / 2

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (phase <= 2) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            colors = listOf(MiamiCyan, MiamiPink),
                            start = Offset(0f, centerY),
                            end = Offset((widthPx * waveProgress.value).coerceAtLeast(1f), centerY)
                        )
                    )
                )
            }
            if (phase >= 1) {
                val radius = kotlin.math.max(widthPx, heightPx) * 1.2f
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(
                                MiamiCyan.copy(alpha = flashAlpha.value * 0.25f),
                                MiamiPink.copy(alpha = flashAlpha.value * 0.15f),
                                Color.White.copy(alpha = flashAlpha.value * 0.1f),
                                Color.Transparent
                            ),
                            center = Offset(widthPx / 2, heightPx / 2),
                            radius = radius
                        )
                    )
                )
            }
            if (phase >= 2) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = blackFadeAlpha.value))
                )
            }
            if (phase >= 2) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = (-80).dp)
                ) {
                    Text(
                        text = "Spoticious",
                        modifier = Modifier.alpha(textAlpha.value),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            brush = Brush.horizontalGradient(colors = listOf(MiamiCyan, MiamiPink))
                        )
                    )
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(160.dp)
                            .offset(y = offsetY.dp)
                            .alpha(logoAlpha)
                            .shadow(
                                elevation = (24 * glowAlpha).dp,
                                shape = CircleShape,
                                spotColor = Color(0xFF00E5FF),
                                ambientColor = Color(0xFFB040FF)
                            )
                    )
                }
            }
        }
    }
}
