package com.codestudio71.spoticious.data.wrapped

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PlayEvent::class], version = 1, exportSchema = false)
abstract class SpoticiousDatabase : RoomDatabase() {

    abstract fun playEventDao(): PlayEventDao

    companion object {
        @Volatile
        private var instance: SpoticiousDatabase? = null

        fun get(context: Context): SpoticiousDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpoticiousDatabase::class.java,
                    "spoticious_wrapped.db",
                ).build().also { instance = it }
            }
    }
}
