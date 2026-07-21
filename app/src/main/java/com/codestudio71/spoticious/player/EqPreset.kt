package com.codestudio71.spoticious.player

import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class EqPreset(
    val name: String,
    val gains: List<Float>,
    val enabled: Boolean = true,
    /** dB; domyślnie 0 dla starych presetów w DataStore bez pola. */
    val preamp: Float = 0f,
) {
    fun normalizedBandGains(): List<Float> =
        (0 until 10).map { i -> gains.getOrElse(i) { 0f }.coerceIn(-15f, 15f) }

    fun normalizedPreamp(): Float = preamp.coerceIn(-15f, 15f)
}

@Serializable
private data class EqPresetsEnvelope(
    val presets: List<EqPreset> = emptyList()
)

/** Wynik [PlayerViewModel.savePreset]. */
sealed class EqPresetSaveOutcome {
    data object Saved : EqPresetSaveOutcome()
    data object DuplicateRequiresConfirmation : EqPresetSaveOutcome()
    data class Error(val message: String) : EqPresetSaveOutcome()
}

/** Wynik importu pliku Audacious. */
sealed class EqPresetImportOutcome {
    data class Imported(
        val added: Int,
        val overwritten: Int,
        val skipped: Int,
    ) : EqPresetImportOutcome()

    /** Są konflikty nazw — UI pyta: nadpisz / pomiń / anuluj. */
    data class NeedsConflictResolution(
        val pending: List<EqPreset>,
        val conflictNames: List<String>,
    ) : EqPresetImportOutcome()

    data class Error(val message: String) : EqPresetImportOutcome()
}

enum class EqPresetImportConflictMode {
    Overwrite,
    SkipExisting,
}

object EqPresetJsonCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(presets: List<EqPreset>): String =
        json.encodeToString(
            EqPresetsEnvelope(presets.sortedBy { it.name.lowercase(Locale.getDefault()) })
        )

    fun decodeOrEmpty(raw: String): List<EqPreset> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<EqPresetsEnvelope>(raw).presets.map { normalize(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun normalize(p: EqPreset): EqPreset {
        val g = p.gains.take(10)
        val padded = g + List((10 - g.size).coerceAtLeast(0)) { 0f }
        return p.copy(
            gains = padded.take(10).map { it.coerceIn(-15f, 15f) },
            preamp = p.preamp.coerceIn(-15f, 15f),
        )
    }
}
