package com.codestudio71.spoticious.data.wrapped

/** Artysta z tagu lub wzorca „Artysta - Tytuł” w nazwie pliku/tytule. */
object PlayEventArtistResolver {

    private const val UNKNOWN = "Unknown"
    private const val SEPARATOR = " - "

    fun resolve(title: String, tagArtist: String?): String {
        tagArtist?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val base = title.substringBeforeLast('.').trim()
        val idx = base.indexOf(SEPARATOR)
        if (idx > 0) {
            val parsed = base.substring(0, idx).trim()
            if (parsed.isNotEmpty()) return parsed
        }
        return UNKNOWN
    }
}
