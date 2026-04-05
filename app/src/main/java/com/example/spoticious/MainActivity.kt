package com.example.spoticious

import android.content.Intent
import android.os.Bundle
import com.example.spoticious.player.PendingExternalAudio
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.example.spoticious.player.PlayerViewModel
import com.example.spoticious.player.PlaybackService
import com.example.spoticious.ui.screens.MainScreen
import com.example.spoticious.ui.screens.SplashScreen
import com.example.spoticious.ui.theme.SpoticiousTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PendingExternalAudio.enqueueFromIntentIfViewAction(intent)
        startService(Intent(this, PlaybackService::class.java))
        enableEdgeToEdge()
        setContent {
            SpoticiousTheme {
                var showMenu by remember { mutableStateOf(false) }
                val menuAlpha = remember { Animatable(0f) }

                LaunchedEffect(showMenu) {
                    if (showMenu) {
                        menuAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(1500, easing = LinearEasing)
                        )
                    }
                }

                if (showMenu) {
                    MainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(menuAlpha.value)
                    )
                } else {
                    SplashScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        onTransitionComplete = { showMenu = true }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            ViewModelProvider(this)[PlayerViewModel::class.java].playExternalUri(intent.data!!)
        }
    }
}
