package com.example.spoticious.folders

import android.app.Application
import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentValues
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Usuwanie / zmiana nazwy utworów w MediaStore (utwory z listy głównej).
 * Na Android 11+ używa [MediaStore.createDeleteRequest] / [MediaStore.createWriteRequest].
 */
sealed class TrackMediaOpResult {
    object Success : TrackMediaOpResult()
    data class NeedIntentSender(val intentSender: IntentSender) : TrackMediaOpResult()
    data class Failed(val message: String) : TrackMediaOpResult()
}

fun requestDeleteTrack(app: Application, uri: Uri): TrackMediaOpResult {
    val resolver = app.contentResolver
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pending = MediaStore.createDeleteRequest(resolver, listOf(uri))
            TrackMediaOpResult.NeedIntentSender(pending.intentSender)
        } else {
            val n = resolver.delete(uri, null, null)
            if (n > 0) TrackMediaOpResult.Success
            else TrackMediaOpResult.Failed("Nie udało się usunąć pliku.")
        }
    } catch (e: RecoverableSecurityException) {
        intentSenderFromRecoverable(e)
    } catch (e: SecurityException) {
        TrackMediaOpResult.Failed(e.message ?: "Brak uprawnień do usunięcia.")
    } catch (e: Exception) {
        TrackMediaOpResult.Failed(e.message ?: "Błąd usuwania.")
    }
}

fun requestWriteAccessForRename(app: Application, uri: Uri): TrackMediaOpResult {
    val resolver = app.contentResolver
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pending = MediaStore.createWriteRequest(resolver, listOf(uri))
            TrackMediaOpResult.NeedIntentSender(pending.intentSender)
        } else {
            // Starsze wersje: spróbuj od razu update; przy braku dostępu dostaniemy Recoverable.
            TrackMediaOpResult.Success
        }
    } catch (e: Exception) {
        TrackMediaOpResult.Failed(e.message ?: "Błąd przygotowania zapisu.")
    }
}

fun applyDisplayNameUpdate(resolver: ContentResolver, uri: Uri, newDisplayName: String): TrackMediaOpResult {
    val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, newDisplayName.trim())
    }
    return try {
        val n = resolver.update(uri, values, null, null)
        if (n > 0) TrackMediaOpResult.Success
        else TrackMediaOpResult.Failed("Nie udało się zmienić nazwy.")
    } catch (e: RecoverableSecurityException) {
        intentSenderFromRecoverable(e)
    } catch (e: SecurityException) {
        TrackMediaOpResult.Failed(e.message ?: "Brak uprawnień do zmiany nazwy.")
    } catch (e: Exception) {
        TrackMediaOpResult.Failed(e.message ?: "Błąd zapisu.")
    }
}

private fun intentSenderFromRecoverable(e: RecoverableSecurityException): TrackMediaOpResult {
    return try {
        val action = e.userAction.actionIntent
        val sender = when (action) {
            is PendingIntent -> action.intentSender
            is IntentSender -> action
            else -> return TrackMediaOpResult.Failed(e.message ?: "Wymagane potwierdzenie systemu.")
        }
        TrackMediaOpResult.NeedIntentSender(sender)
    } catch (e2: Exception) {
        TrackMediaOpResult.Failed(e.message ?: "Wymagane potwierdzenie systemu.")
    }
}

/** Zachowuje rozszerzenie pliku, jeśli użytkownik wpisze sam tytuł. */
fun buildSafeDisplayName(currentDisplayName: String, userInput: String): String {
    val trimmed = userInput.trim()
    if (trimmed.isEmpty()) return currentDisplayName
    val sanitized = trimmed.replace(Regex("[/\\\\:*?\"<>|]"), "_").trimEnd('.')
    if (sanitized.isEmpty()) return currentDisplayName
    val hadExtInInput = sanitized.contains('.') && sanitized.substringAfterLast('.').let { it.length in 1..5 }
    return if (hadExtInInput) sanitized else {
        val ext = currentDisplayName.substringAfterLast('.', "")
        if (ext.isNotEmpty() && !sanitized.endsWith(".$ext", ignoreCase = true)) "$sanitized.$ext" else sanitized
    }
}
