package com.example.spoticious.player

import android.content.Intent
import android.net.Uri
import java.util.ArrayDeque

/**
 * URIs from [Intent.ACTION_VIEW] (Open with). Polled once when [PlayerViewModel] starts
 * or from [MainScreen] onResume (e.g. after [android.app.Activity.onNewIntent]).
 */
object PendingExternalAudio {

    private val queue = ArrayDeque<Uri>()

    @Synchronized
    fun enqueueFromIntentIfViewAction(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { queue.addLast(it) }
        }
    }

    @Synchronized
    fun poll(): Uri? = queue.poll()
}
