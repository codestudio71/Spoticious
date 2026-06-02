package com.codestudio71.spoticious.data.wrapped

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class TitlePlayCount(
    val title: String,
    val playCount: Int,
)

data class ArtistPlayCount(
    val artist: String?,
    val playCount: Int,
)

@Dao
interface PlayEventDao {

    @Insert
    suspend fun insert(event: PlayEvent)

    @Query(
        """
        SELECT title AS title, COUNT(*) AS playCount
        FROM play_events
        WHERE playedAtMs >= :sinceMs AND qualified = 1
        GROUP BY title
        ORDER BY playCount DESC
        LIMIT 10
        """,
    )
    suspend fun topTracks(sinceMs: Long): List<TitlePlayCount>

    @Query(
        """
        SELECT artist AS artist, COUNT(*) AS playCount
        FROM play_events
        WHERE playedAtMs >= :sinceMs AND qualified = 1
        GROUP BY artist
        ORDER BY playCount DESC
        LIMIT 10
        """,
    )
    suspend fun topArtists(sinceMs: Long): List<ArtistPlayCount>

    @Query(
        """
        SELECT COUNT(*) FROM play_events
        WHERE playedAtMs >= :sinceMs AND qualified = 1
        """,
    )
    suspend fun totalPlays(sinceMs: Long): Int

    @Query(
        """
        SELECT COALESCE(SUM(durationMs), 0) FROM play_events
        WHERE playedAtMs >= :sinceMs AND qualified = 1
        """,
    )
    suspend fun totalQualifiedTimeMs(sinceMs: Long): Long
}
