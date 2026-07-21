package com.codestudio71.spoticious.player

/**
 * Parser plików presetów Audacious (INI / plain text).
 *
 * Wspiera:
 * - `~/.config/audacious/eq.preset` (wiele sekcji + opcjonalnie `[Presets]`)
 * - pojedynczy export `*.preset` (jedna sekcja `[Nazwa]`)
 * - float z kropką lub przecinkiem (PL Windows)
 *
 * Wartości = dB. Band0…Band9 = 10 pasm.
 */
object AudaciousEqPresetParser {

    data class Parsed(
        val name: String,
        val preamp: Float,
        val bands: List<Float>,
    )

    fun parse(
        text: String,
        fallbackName: String = "Imported",
    ): List<Parsed> {
        if (text.isBlank()) return emptyList()

        val sections = linkedMapOf<String, MutableMap<String, String>>()
        var current: String? = null

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue

            if (line.startsWith("[") && line.endsWith("]") && line.length >= 3) {
                current = line.substring(1, line.length - 1).trim()
                if (current.isNotEmpty()) {
                    sections.getOrPut(current) { mutableMapOf() }
                }
                continue
            }

            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            val section = current ?: "_root"
            sections.getOrPut(section) { mutableMapOf() }[key] = value
        }

        val orderedNames = mutableListOf<String>()
        sections["Presets"]?.entries
            ?.sortedBy { it.key.lowercase() }
            ?.forEach { (k, v) ->
                if (k.startsWith("Preset", ignoreCase = true) && v.isNotBlank()) {
                    orderedNames.add(v.trim())
                }
            }

        val result = linkedMapOf<String, Parsed>()

        fun ingest(sectionName: String, map: Map<String, String>, nameOverride: String? = null) {
            if (sectionName.equals("Presets", ignoreCase = true)) return
            val hasBands = (0 until 10).any { map.containsKey("Band$it") }
            val hasPreamp = map.containsKey("Preamp")
            if (!hasBands && !hasPreamp) return

            val name =
                (nameOverride ?: sectionName.takeUnless { it == "_root" } ?: fallbackName)
                    .trim()
                    .ifBlank { fallbackName }
            val preamp = parseDb(map["Preamp"]) ?: 0f
            val bands =
                (0 until 10).map { i ->
                    parseDb(map["Band$i"]) ?: 0f
                }
            result[name] = Parsed(name = name, preamp = preamp, bands = bands)
        }

        for (name in orderedNames) {
            val map = sections[name] ?: sections.entries
                .firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value
            if (map != null) ingest(name, map, nameOverride = name)
        }

        for ((sectionName, map) in sections) {
            if (sectionName.equals("Presets", ignoreCase = true)) continue
            if (result.keys.any { it.equals(sectionName, ignoreCase = true) }) continue
            ingest(sectionName, map)
        }

        // Plik bez sekcji: same klucze w _root
        sections["_root"]?.let { map ->
            if (result.isEmpty()) ingest("_root", map, nameOverride = fallbackName)
        }

        return result.values.toList()
    }

    /** dB: "5.6", "5,6", "-1.11022e-15" */
    private fun parseDb(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        // PL: przecinek dziesiętny, ale nie ruszaj notacji naukowej z przecinkiem jako tysiącami
        if (s.contains(',') && !s.contains('.')) {
            s = s.replace(',', '.')
        } else if (s.contains(',') && s.contains('.')) {
            // 1.234,56 → usuń kropki tysięcy, przecinek → kropka
            s = s.replace(".", "").replace(',', '.')
        }
        return s.toFloatOrNull()
    }
}
