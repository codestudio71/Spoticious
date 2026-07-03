package com.codestudio71.spoticious.audio.record

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.codestudio71.spoticious.MainActivity
import com.codestudio71.spoticious.R
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service typu microphone|mediaPlayback — bez niego Android 12+ wycisza
 * [android.media.AudioRecord] ~10 s po zejściu apki do tła (kropka mikrofonu znika,
 * w pliku cisza). Sam stan nagrywania trzyma [RecordingSession]; serwis tylko
 * utrzymuje proces przy życiu i pokazuje notyfikację z timerem + akcją Stop.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startInForeground()
        observeSession()
    }

    private fun startInForeground() {
        val notification = buildNotification(0L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeSession() {
        // Timer w notyfikacji: sekundowa rozdzielczość wystarcza, unikamy spamu co 100 ms.
        scope.launch {
            RecordingSession.recordingElapsedMs
                .map { it / 1000L }
                .distinctUntilChanged()
                .collect { seconds ->
                    if (RecordingSession.recordingState.value is RecordingState.Recording) {
                        val manager = getSystemService(NotificationManager::class.java)
                        manager.notify(NOTIFICATION_ID, buildNotification(seconds * 1000L))
                    }
                }
        }
        // Koniec pracy: nagranie sfinalizowane i mix skończony -> serwis niepotrzebny.
        // Uwaga: stan początkowy to Idle — bez flagi sawRecording serwis ubiłby się od razu
        // po starcie, zanim VocalRecorder zdąży przejść w Recording.
        scope.launch {
            var sawRecording = false
            combine(
                RecordingSession.recordingState,
                RecordingSession.mixInProgress,
            ) { state, mixing -> state to mixing }
                .distinctUntilChanged()
                .collect { (state, mixing) ->
                    if (state is RecordingState.Recording || state is RecordingState.Stopping) {
                        sawRecording = true
                    }
                    val done =
                        (state is RecordingState.Saved || state is RecordingState.Idle) && !mixing
                    if (sawRecording && done) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_RECORDING) {
            RecordingSession.stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.record_notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(elapsedMs: Long): android.app.Notification {
        val openIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val openPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                REQUEST_CODE_STOP,
                Intent(this, RecordingService::class.java).apply { action = ACTION_STOP_RECORDING },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val seconds = (elapsedMs / 1000L).coerceAtLeast(0L)
        val timer =
            String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.record_notif_title))
            .setContentText(getString(R.string.record_notif_text, timer))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.record_notif_stop),
                stopPendingIntent,
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "spoticious_recording"
        private const val NOTIFICATION_ID = 2
        private const val REQUEST_CODE_STOP = 10

        const val ACTION_STOP_RECORDING = "com.codestudio71.spoticious.STOP_RECORDING"

        /** Wołać PRZED [android.media.AudioRecord.startRecording] (użytkownik jest na foreground). */
        fun start(context: Context) {
            val i = Intent(context, RecordingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}
