package com.us.copilot.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.us.copilot.data.local.dao.AnalysisDao
import com.us.copilot.data.local.dao.CheckInDao
import com.us.copilot.data.local.dao.MemoryDao
import com.us.copilot.data.local.dao.ProfileDao
import com.us.copilot.data.local.entity.AnalysisEntity
import com.us.copilot.data.local.entity.CheckInEntity
import com.us.copilot.data.local.entity.MemoryEntity
import com.us.copilot.data.local.entity.ProfileEntity

@Database(
    entities = [
        ProfileEntity::class,
        MemoryEntity::class,
        CheckInEntity::class,
        AnalysisEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class UsDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun memoryDao(): MemoryDao
    abstract fun checkInDao(): CheckInDao
    abstract fun analysisDao(): AnalysisDao

    companion object {
        const val NAME = "us.db"
    }
}
