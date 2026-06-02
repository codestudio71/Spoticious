package com.codestudio71.spoticious.data.wrapped

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_events")
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackUri: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val playedAtMs: Long,
    val qualified: Boolean,
)
