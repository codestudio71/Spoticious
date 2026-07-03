package com.codestudio71.spoticious.data.wrapped

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class TitlePlayCount(
    val title: String,
    val playCount: Int,
)

data class TitleListenTime(
    val title: String,
    val listenedMs: Long,
)

@Dao
interface PlayEventDao {

    @Insert
    suspend fun insert(event: PlayEvent): Long

    @Query("UPDATE play_events SET listenedMs = :listenedMs WHERE id = :id")
    suspend fun updateListenedMs(id: Long, listenedMs: Long)

    @Query("UPDATE play_events SET qualified = 1 WHERE id = :id")
    suspend fun markQualified(id: Long)

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
        SELECT COUNT(*) FROM play_events
        WHERE playedAtMs >= :sinceMs AND qualified = 1
        """,
    )
    suspend fun totalPlays(sinceMs: Long): Int

    // Czas liczony ze WSZYSTKICH sesji (także < progu 30 s) — inaczej statystyka
    // "skacze" dopiero po kwalifikacji i wygląda na liczoną co 30 s.
    @Query(
        """
        SELECT COALESCE(SUM(listenedMs), 0) FROM play_events
        WHERE playedAtMs >= :sinceMs
        """,
    )
    suspend fun totalListenedTimeMs(sinceMs: Long): Long

    @Query(
        """
        SELECT title AS title, COALESCE(SUM(listenedMs), 0) AS listenedMs
        FROM play_events
        WHERE playedAtMs >= :sinceMs
        GROUP BY title
        ORDER BY listenedMs DESC
        LIMIT 10
        """,
    )
    suspend fun topTracksByTime(sinceMs: Long): List<TitleListenTime>
}
