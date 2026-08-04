package com.codestudio71.spoticious

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.codestudio71.spoticious.data.UiPrefKeys
import com.codestudio71.spoticious.data.uiPreferencesDataStore
import com.codestudio71.spoticious.player.PendingExternalAudio
import com.codestudio71.spoticious.player.PlaybackService
import com.codestudio71.spoticious.player.PlayerViewModel
import com.codestudio71.spoticious.ui.screens.MainScreen
import com.codestudio71.spoticious.ui.screens.SplashScreen
import com.codestudio71.spoticious.ui.theme.SpoticiousLookId
import com.codestudio71.spoticious.ui.theme.SpoticiousTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PendingExternalAudio.enqueueFromIntentIfViewAction(intent)
        PlaybackService.ensureStarted(this)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        window.navigationBarColor = AndroidColor.parseColor("#0D0D1A")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val context = LocalContext.current
            val lookId by context.uiPreferencesDataStore.data
                .map { prefs -> SpoticiousLookId.fromStorageKey(prefs[UiPrefKeys.APP_LOOK]) }
                .collectAsState(initial = SpoticiousLookId.MIAMI)

            SpoticiousTheme(lookId = lookId) {
                val resumeWithPlayer = PlaybackService.player?.currentMediaItem != null
                var showMenu by remember { mutableStateOf(resumeWithPlayer) }
                val menuAlpha =
                    remember {
                        Animatable(if (resumeWithPlayer) 1f else 0f)
                    }

                LaunchedEffect(showMenu) {
                    if (showMenu && menuAlpha.value < 1f) {
                        menuAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(1500, easing = LinearEasing),
                        )
                    }
                }

                if (showMenu) {
                    MainScreen(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .alpha(menuAlpha.value),
                    )
                } else {
                    SplashScreen(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                        onTransitionComplete = { showMenu = true },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when {
            intent.action == Intent.ACTION_VIEW && intent.data != null ->
                ViewModelProvider(this)[PlayerViewModel::class.java].playExternalUri(intent.data!!)
            else -> { /* np. powrót z notyfikacji — bez cold-init UI */ }
        }
    }
}
