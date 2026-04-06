package com.codestudio71.spoticious.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.codestudio71.spoticious.MainActivity
import com.codestudio71.spoticious.R
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player

class PlaybackService : Service() {

    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        eqProcessor = EqualizerAudioProcessor()
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(EqRenderersFactory(this, eqProcessor!!))
            .build()

        mediaSession = MediaSessionCompat(this, "Spoticious").apply {
            isActive = true
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    player?.play()
                }

                override fun onPause() {
                    player?.pause()
                }

                override fun onSkipToNext() {
                    onSkipToNextCallback?.invoke() ?: player?.seekToNext()
                }

                override fun onSkipToPrevious() {
                    onSkipToPreviousCallback?.invoke() ?: player?.seekToPrevious()
                }

                override fun onStop() {
                    handleStop()
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    if (action == CUSTOM_ACTION_STOP) handleStop()
                }
            })
        }

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                updatePlaybackState()
                updateNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlaybackState()
                updateNotification()
            }

            override fun onMediaItemTransition(mediaItem: com.google.android.exoplayer2.MediaItem?, reason: Int) {
                updatePlaybackState()
                updateNotification()
            }
        })

        createNotificationChannel()
        updatePlaybackState()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                handleStop()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> player?.play()
            ACTION_PAUSE -> player?.pause()
            ACTION_NEXT -> onSkipToNextCallback?.invoke() ?: player?.seekToNext()
            ACTION_PREV -> onSkipToPreviousCallback?.invoke() ?: player?.seekToPrevious()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        eqProcessor = null
        instance = null
        super.onDestroy()
    }

    private fun handleStop() {
        Log.d("PlaybackService", "handleStop called")
        val p = player ?: return
        val uri = p.currentMediaItem?.localConfiguration?.uri?.toString()
        if (uri != null) {
            val repo = PlaybackStateRepository(this)
            val lastState = repo.getLastPlaybackState()
            val fileName = if (lastState?.uri == uri) lastState.fileName else getString(R.string.track_default)
            repo.savePlaybackState(uri, p.currentPosition, fileName)
        }
        p.stop()
        p.clearMediaItems()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        mediaSession?.isActive = false
    }

    private fun updatePlaybackState() {
        val p = player ?: return
        val session = mediaSession ?: return

        val state = when (p.playbackState) {
            Player.STATE_READY -> if (p.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            Player.STATE_IDLE -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_NONE
        }

        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP

        val stopCustomAction = PlaybackStateCompat.CustomAction.Builder(
            CUSTOM_ACTION_STOP,
            getString(R.string.notif_action_stop),
            android.R.drawable.ic_delete
        ).build()

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, p.currentPosition, if (p.isPlaying) 1f else 0f)
            .addCustomAction(stopCustomAction)
            .build()

        session.setPlaybackState(playbackState)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deleteIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_DELETE,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isPlaying = player?.isPlaying == true
        val playPauseIntent = Intent(this, PlaybackService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 0, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 0, Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sessionToken = mediaSession?.sessionToken
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (isPlaying) getString(R.string.notif_playing) else getString(R.string.notif_paused))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openPendingIntent)
            .setDeleteIntent(deleteIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_media_previous, getString(R.string.notif_action_previous), prevIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) getString(R.string.notif_action_pause) else getString(R.string.notif_action_play),
                playPausePendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, getString(R.string.notif_action_next), nextIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.notif_action_stop), stopPendingIntent)

        if (sessionToken != null) {
            // MediaStyle allows at most 3 actions in compact view — with 4 addActions,
            // using (0,1,2) hid "Stop" (index 3). Use (0,1,3): prev, play/pause, stop; next is index 2, expanded only.
            builder.setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(sessionToken)
                    .setShowActionsInCompactView(0, 1, 3)
            )
        }

        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "spoticious_playback"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_CODE_STOP = 4
        private const val REQUEST_CODE_DELETE = 5

        /** Intent action for notification “Stop” / swipe-dismiss — handled in onStartCommand. */
        const val ACTION_STOP = "ACTION_STOP_SERVICE"

        /** MediaSession custom action — shown on some devices when standard STOP is hidden. */
        private const val CUSTOM_ACTION_STOP = "com.codestudio71.spoticious.CUSTOM_STOP"
        private const val ACTION_PLAY = "com.codestudio71.spoticious.PLAY"
        private const val ACTION_PAUSE = "com.codestudio71.spoticious.PAUSE"
        private const val ACTION_NEXT = "com.codestudio71.spoticious.NEXT"
        private const val ACTION_PREV = "com.codestudio71.spoticious.PREV"

        var onSkipToNextCallback: (() -> Unit)? = null
        var onSkipToPreviousCallback: (() -> Unit)? = null

        @Volatile
        var instance: PlaybackService? = null

        @Volatile
        var player: ExoPlayer? = null

        @Volatile
        var eqProcessor: EqualizerAudioProcessor? = null
    }
}
