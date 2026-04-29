package com.codestudio71.spoticious

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Procesowy scope dla pracy w tle (Master Data itd.) — nie jest powiązany z Activity / ViewModel.
 */
class SpoticiousApplication : Application() {

    /** SupervisorJob: błąd w jednym child nie anuluje całego scope. */
    val masterDataScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        /** Ustawiane wyłącznie w [onCreate] — singleton procesu. */
        @Volatile
        var instance: SpoticiousApplication? = null
    }
}
